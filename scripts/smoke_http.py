"""Real Cookie HTTP acceptance checks; Python standard library only.

This script requires an explicitly seeded synthetic acceptance environment. It
creates temporary synthetic accounts and removes them in a finally block. It
never prints passwords, Cookie values, or a complete HTTP response body.
"""
from __future__ import annotations

import argparse
import getpass
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import HTTPCookieProcessor, Request, build_opener

ROOT = Path(__file__).resolve().parents[1]


class CheckFailure(RuntimeError):
    pass


class Client:
    def __init__(self, base_url: str, timeout=20):
        self.base = base_url.rstrip('/')
        self.timeout = timeout
        self.cookies = http.cookiejar.CookieJar()
        self.opener = build_opener(HTTPCookieProcessor(self.cookies))

    def request(self, method, path, body=None, expected=200):
        raw = None if body is None else json.dumps(body).encode('utf-8')
        req = Request(self.base + path, data=raw, method=method,
                      headers={'Accept': 'application/json', 'Content-Type': 'application/json'})
        try:
            with self.opener.open(req, timeout=self.timeout) as response:
                status, data = response.status, response.read()
        except HTTPError as error:
            status, data = error.code, error.read()
        try:
            payload = json.loads(data) if data else None
        except (ValueError, UnicodeDecodeError):
            payload = None
        statuses = (expected,) if isinstance(expected, int) else expected
        if status not in statuses:
            code = error_code(payload)
            raise CheckFailure(f'{method} {path}: expected {statuses}, got {status}, code={code}')
        return payload

    def login(self, account, password):
        data = self.request('POST', '/api/v1/auth/login', {'account': account, 'password': password})
        if not any(cookie.name == 'JSESSIONID' for cookie in self.cookies):
            raise CheckFailure('Login did not establish a JSESSIONID Cookie.')
        return data


def error_code(data):
    if not isinstance(data, dict):
        return 'non-JSON'
    error = data.get('error', data)
    return error.get('code', 'unknown') if isinstance(error, dict) else 'unknown'


def check(condition, message):
    if not condition:
        raise CheckFailure(message)
    print('PASS ' + message)


def wait_ready(base_url, timeout):
    deadline = time.monotonic() + timeout
    health = Client(base_url, timeout=3)
    while True:
        try:
            payload = health.request('GET', '/api/v1/health')
            if isinstance(payload, dict) and payload.get('status') == 'UP':
                check(True, 'HTTP entry is ready after backend recreation')
                return
        except (CheckFailure, URLError, OSError):
            pass
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise CheckFailure('HTTP entry did not become ready within the requested wait.')
        time.sleep(min(1, remaining))


def rows(data):
    if not isinstance(data, dict) or not isinstance(data.get('items'), list):
        raise CheckFailure('Expected the contracted {items,page,pageSize,total} page response.')
    return data['items']


def load_password(args):
    values = {}
    if args.env_file:
        for line in Path(args.env_file).read_text(encoding='utf-8-sig').splitlines():
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            key, separator, value = line.partition('=')
            if separator:
                value = value.strip()
                if len(value) > 1 and value[0] == value[-1] and value[0] in {'"', "'"}:
                    value = value[1:-1]
                values[key.strip()] = value
    password = os.environ.get(args.password_env) or values.get(args.password_env)
    if not password:
        if not sys.stdin.isatty():
            raise CheckFailure(f'Set {args.password_env} or pass --env-file; no password was supplied.')
        password = getpass.getpass('Synthetic backend acceptance password: ')
    if 'REPLACE_' in password:
        raise CheckFailure('The password is still a placeholder.')
    return password


def admin_context(args, password):
    client = Client(args.base_url)
    client.login(args.admin_account, password)
    me = client.request('GET', '/api/v1/me')
    roles = rows(client.request('GET', '/api/v1/roles?pageSize=100'))
    by_code = {role['code'].upper().replace('-', '_'): str(role['id']) for role in roles}
    if 'ADMIN' not in by_code or 'STAFF' not in by_code:
        raise CheckFailure('Seeded ADMIN and STAFF roles are required for this self-test.')
    return client, me, by_code


def create_synthetic_user(client, department_id, staff_role, initial_password, employee_no=None):
    employee_no = employee_no or ('DEMO_SMOKE_' + secrets.token_hex(5))
    created = client.request('POST', '/api/v1/users', {
        'name': '合成验收账号', 'username': employee_no,
        'departmentId': str(department_id),
    }, expected=201)
    user_id = str(created['id'])
    path = '/api/v1/users/' + quote(user_id, safe='')
    try:
        current = client.request('GET', path + '/roles')
        client.request('PUT', path + '/roles', {'roleIds': [staff_role], 'version': current['version']})
    except Exception:
        client.request('DELETE', path, expected=204)
        raise
    return user_id, employee_no, initial_password


def department_subtree(tree, root_id):
    def all_ids(node):
        return {str(node['id'])}.union(*(all_ids(child) for child in node.get('children', [])))
    for node in tree:
        if str(node['id']) == str(root_id):
            return all_ids(node)
        found = department_subtree(node.get('children', []), root_id)
        if found:
            return found
    return set()


def check_web(base_url):
    opener = build_opener()
    for path, token in [('/', b'<html'), ('/stage1/', b'<html'),
                        ('/api-design/openapi.yaml', b'openapi:'),
                        ('/swagger-ui/index.html', b'swagger'),
                        ('/v3/api-docs', b'openapi')]:
        with opener.open(base_url.rstrip('/') + path, timeout=20) as response:
            content = response.read()
            check(response.status == 200 and token in content.lower(), 'Web/API document entry ' + path)


def smoke(args, password):
    anonymous = Client(args.base_url)
    health = anonymous.request('GET', '/api/v1/health')
    check(health.get('status') == 'UP', 'Backend/database readiness')
    anonymous.request('GET', '/api/v1/me', expected=401)
    check(True, 'Anonymous request returns 401')
    anonymous.request('POST', '/api/v1/auth/login', {'account': args.admin_account, 'password': secrets.token_hex(16)}, expected=401)
    check(True, 'Wrong password returns 401')
    admin, admin_me, roles = admin_context(args, password)
    check(True, 'Administrator login establishes Cookie session')

    staff = Client(args.base_url)
    staff.login(args.staff_account, password)
    staff_me = staff.request('GET', '/api/v1/me')
    staff.request('GET', '/api/v1/roles', expected=403)
    check(True, 'Ordinary employee cannot call role administration (403)')
    staff_rows = rows(staff.request('GET', '/api/v1/ledger/employees?pageSize=100'))
    check(bool(staff_rows) and all(str(row['id']) == str(staff_me['id']) for row in staff_rows),
          'SELF scope returns the current employee only')
    staff.request('PATCH', '/api/v1/ledger/employees/' + quote(str(admin_me['id']), safe=''),
                  {'email': 'synthetic-smoke@example.invalid'}, expected=403)
    check(True, 'SELF scope rejects an update to another employee')

    dept = Client(args.base_url)
    dept.login(args.dept_account, password)
    dept_me = dept.request('GET', '/api/v1/me')
    allowed_departments = department_subtree(admin.request('GET', '/api/v1/departments'), dept_me['departmentId'])
    dept_rows = rows(dept.request('GET', '/api/v1/users?pageSize=100'))
    check(bool(dept_rows) and all(str(row['departmentId']) in allowed_departments for row in dept_rows),
          'DEPT scope lists only users in the current department subtree')
    check(str(admin_me['departmentId']) not in allowed_departments, 'Cross-department fixture is distinct')
    dept.request('GET', '/api/v1/users/' + quote(str(admin_me['id']), safe=''), expected=403)
    check(True, 'Direct cross-department ID request returns 403')

    admin_id = quote(str(admin_me['id']), safe='')
    admin.request('PATCH', f'/api/v1/users/{admin_id}/status', {'status': 'DISABLED'}, expected=409)
    admin.request('DELETE', f'/api/v1/users/{admin_id}', expected=409)
    assignment = admin.request('GET', f'/api/v1/users/{admin_id}/roles')
    admin.request('PUT', f'/api/v1/users/{admin_id}/roles', {'roleIds': [], 'version': assignment['version']}, expected=409)
    check(True, 'Unique administrator cannot be disabled, deleted, or stripped of roles')

    temporary_id = None
    temporary_role_id = None
    try:
        temporary_id, employee_no, initial = create_synthetic_user(admin, staff_me['departmentId'], roles['STAFF'], password)
        resource = '/api/v1/users/' + quote(temporary_id, safe='')
        temp = Client(args.base_url)
        temp.login(employee_no, initial)
        temp.request('GET', '/api/v1/ledger/employees', expected=403)
        changed_password = 'Changed-' + secrets.token_hex(12)
        temp.request('PATCH', '/api/v1/me/password', {'oldPassword': initial, 'newPassword': changed_password})
        temp = Client(args.base_url)
        temp.login(employee_no, changed_password)
        temp.request('GET', '/api/v1/ledger/employees')
        check(True, 'New account must change password before accessing business data')

        state = admin.request('GET', resource + '/roles')
        body = {'roleIds': state['roleIds'], 'version': state['version']}
        saved = admin.request('PUT', resource + '/roles', body)
        check(saved['version'] == state['version'] + 1, 'Role assignment increments its version')
        conflict = admin.request('PUT', resource + '/roles', body, expected=409)
        check(error_code(conflict) == 'VERSION_CONFLICT', 'Stale assignment returns 409 VERSION_CONFLICT')
        latest = admin.request('GET', resource + '/roles')
        admin.request('PUT', resource + '/roles', {'roleIds': [roles['ADMIN']], 'version': latest['version']}, expected=403)
        check(True, 'Super administrator role cannot be granted to another account')

        created_role = admin.request('POST', '/api/v1/roles', {'code': 'SMOKE_' + secrets.token_hex(5), 'name': '合成验收角色'}, expected=201)
        temporary_role_id = str(created_role['id'])
        grant_path = '/api/v1/roles/' + quote(temporary_role_id, safe='') + '/permissions'
        current_grants = admin.request('GET', grant_path)
        staff_grants = admin.request('GET', '/api/v1/roles/' + roles['STAFF'] + '/permissions')['grants']
        read_grants = [grant for grant in staff_grants if grant['code'].endswith(':read')]
        check(bool(read_grants), 'Synthetic STAFF role supplies a business read grant fixture')
        grant_body = {'grants': [read_grants[0]], 'version': current_grants['version']}
        updated_grants = admin.request('PUT', grant_path, grant_body)
        conflict = admin.request('PUT', grant_path, grant_body, expected=409)
        check(error_code(conflict) == 'VERSION_CONFLICT', 'Stale role grants return 409 VERSION_CONFLICT')
        invalid = {'grants': [{'code': 'nonexistent:f999:read', 'scope': 'ALL'}], 'version': updated_grants['version']}
        admin.request('PUT', grant_path, invalid, expected=400)
        after_failure = admin.request('GET', grant_path)
        check(after_failure['version'] == updated_grants['version'] and after_failure['grants'] == updated_grants['grants'],
              'Invalid grant replacement rolls back without partial changes')

        admin.request('PATCH', resource + '/status', {'status': 'DISABLED'})
        temp.request('GET', '/api/v1/me', expected=401)
        temp.request('POST', '/api/v1/auth/login', {'account': employee_no, 'password': changed_password}, expected=401)
        check(True, 'Disabling an account rejects its old session and new login')
    finally:
        if temporary_id is not None:
            admin.request('DELETE', '/api/v1/users/' + quote(temporary_id, safe=''), expected=204)
            print('PASS Temporary synthetic account removed')
        if temporary_role_id is not None:
            admin.request('DELETE', '/api/v1/roles/' + quote(temporary_role_id, safe=''), expected=204)
            print('PASS Temporary synthetic role removed')

    staff.request('POST', '/api/v1/auth/logout')
    staff.request('GET', '/api/v1/me', expected=401)
    check(True, 'Logout invalidates the current Cookie session')
    if args.check_web:
        check_web(args.base_url)


def persistence(args, password):
    state_path = Path(args.state_file)
    admin, me, roles = admin_context(args, password)
    if args.mode == 'persistence-create':
        if state_path.exists():
            raise CheckFailure('A persistence state file exists; verify/clean it before creating a new marker.')
        user_id, employee_no, _ = create_synthetic_user(admin, me['departmentId'], roles['STAFF'], password)
        state_path.parent.mkdir(parents=True, exist_ok=True)
        state_path.write_text(json.dumps({'userId': user_id, 'employeeNo': employee_no}, ensure_ascii=False), encoding='utf-8')
        check(True, 'Persistence marker created; restart backend and database, then run persistence-verify')
    else:
        state = json.loads(state_path.read_text(encoding='utf-8'))
        if not state['employeeNo'].startswith('DEMO_SMOKE_'):
            raise CheckFailure('Refusing to operate on a non-synthetic persistence marker.')
        path = '/api/v1/users/' + quote(str(state['userId']), safe='')
        found = admin.request('GET', path)
        check(found['username'] == state['employeeNo'], 'MySQL persisted the exact synthetic account across restart')
        if args.mode == 'persistence-cleanup':
            admin.request('DELETE', path, expected=204)
            state_path.unlink()
            check(True, 'Persistence marker removed')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1', help='Origin only, without /api/v1')
    parser.add_argument('--env-file', help='Optional local deployment .env; read as data, never executed')
    parser.add_argument('--password-env', default='RBAC_INITIAL_PASSWORD')
    parser.add_argument('--admin-account', default='admin')
    parser.add_argument('--staff-account', default='SG000013')
    parser.add_argument('--dept-account', default='DEMO000100')
    parser.add_argument('--check-web', action='store_true', help='Also check prototype, API design, and Swagger served by Nginx')
    parser.add_argument('--wait-ready', type=int, default=0, metavar='SECONDS', help='Retry readiness after a backend restart/Docker DNS update')
    parser.add_argument('--mode', choices=('smoke', 'persistence-create', 'persistence-verify', 'persistence-cleanup'), default='smoke')
    parser.add_argument('--state-file', default=str(ROOT / 'data/raw/stage1-persistence.json'))
    args = parser.parse_args()
    if not 0 <= args.wait_ready <= 300:
        parser.error('--wait-ready must be between 0 and 300 seconds')
    try:
        password = load_password(args)
        if args.wait_ready:
            wait_ready(args.base_url, args.wait_ready)
        if args.mode == 'smoke':
            smoke(args, password)
        else:
            persistence(args, password)
    except (CheckFailure, URLError, OSError, ValueError, KeyError) as error:
        print(f'FAIL {type(error).__name__}: {error}', file=sys.stderr)
        return 1
    print('HTTP acceptance checks passed.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
