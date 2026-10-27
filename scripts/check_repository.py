"""检查仓库结构、文本编码、文档链接和合成样例引用；不执行应用功能验收。"""
from pathlib import Path
from urllib.parse import unquote, urlsplit
import csv
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    'README.md', 'CONTRIBUTING.md', '.gitignore', '.gitattributes', '.editorconfig',
    '.github/CODEOWNERS', '.github/pull_request_template.md',
    '.github/ISSUE_TEMPLATE/task.yml', '.github/ISSUE_TEMPLATE/bug_report.yml',
    '.github/ISSUE_TEMPLATE/design_question.yml', '.github/ISSUE_TEMPLATE/config.yml',
    '.github/workflows/repository-checks.yml',
    'frontend/README.md', 'backend-procedural/README.md', 'backend-oo/README.md',
    'api/README.md', 'api/openapi.yaml', 'database/migrations/README.md',
    'database/seeds/README.md', 'data/README.md', 'data/raw/.gitkeep',
    'docs/guides/github-team-manual.md', 'docs/planning/team.md',
    'docs/planning/milestones.md', 'docs/standards/api-contract.md',
    'docs/requirements/questions.md', 'docs/testing/acceptance-matrix.md',
    'tests/README.md', 'deploy/README.md', 'deploy/.env.example',
]
FORBIDDEN_SUFFIXES = {'.xlsx', '.xls', '.xlsm', '.pem', '.key', '.p12', '.pfx', '.jks', '.dump', '.bak'}
TEXT_SUFFIXES = {'.md', '.yml', '.yaml', '.json', '.py', '.csv', '.mmd', '.java', '.xml', '.properties', '.html', '.css', '.js', '.cjs', '.mjs', '.ps1'}

def tracked_candidates():
    """同时检查已跟踪和未忽略的新文件，防止尚未 git add 的敏感资料绕过本地检查。"""
    result = subprocess.run(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT, capture_output=True, check=True)
    return sorted({name for name in result.stdout.decode('utf-8').split('\0') if name})

def markdown_links(path, text, errors):
    """检查仓库内 Markdown 目标是否存在且未越界；外链、锚点内容和代码块示例不在此检查范围。"""
    in_fence = False
    for line_number, line in enumerate(text.splitlines(), 1):
        if re.match(r'^\s*(```|~~~)', line):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        for match in re.finditer(r'(?<!!)\[[^\]]+\]\(([^\s)]+)(?:\s+"[^"]*")?\)', line):
            target = match.group(1).strip('<>')
            parts = urlsplit(target)
            if parts.scheme or parts.netloc or not parts.path:
                continue
            resolved = (path.parent / unquote(parts.path)).resolve()
            if not resolved.is_relative_to(ROOT):
                errors.append(f'{path.relative_to(ROOT)}:{line_number}: link leaves repository: {target}')
            elif not resolved.exists():
                errors.append(f'{path.relative_to(ROOT)}:{line_number}: missing local link: {target}')

def sample_data(errors):
    """核对合成员工、部门与负责人引用，不读取 data/raw 中的课程原始资料。"""
    with (ROOT / 'data/samples/users.sample.csv').open(encoding='utf-8', newline='') as file:
        users = list(csv.DictReader(file))
    with (ROOT / 'data/samples/departments.sample.csv').open(encoding='utf-8', newline='') as file:
        departments = list(csv.DictReader(file))
    usernames = {row['username'] for row in users}
    names = {row['name'] for row in departments}
    if len(usernames) != len(users):
        errors.append('Synthetic user samples contain duplicate usernames.')
    for row in users:
        if not row['username'].startswith('DEMO'):
            errors.append('Sample username is not explicitly synthetic.')
        key = row['department'] if row['company'] == '总公司' or row['company'] == row['department'] else row['company'] + '-' + row['department']
        if key not in names:
            errors.append(f'Synthetic department mapping is missing: {key}')
    for row in departments:
        if row['manager_username'] not in usernames:
            errors.append(f'Synthetic manager is missing: {row["name"]}')
        if row['parent_name'] and row['parent_name'] not in names:
            errors.append(f'Synthetic parent department is missing: {row["name"]}')

def main():
    # REQUIRED 描述本仓库协作结构；精简交付版若改变文档布局，应同步维护此清单与引用。
    errors = []
    for name in REQUIRED:
        if not (ROOT / name).is_file():
            errors.append(f'Missing framework file: {name}')
    names = tracked_candidates()
    text_count = 0
    for name in names:
        path = ROOT / name
        if not path.is_file():
            continue
        parts = path.relative_to(ROOT).parts
        if path.suffix.lower() in FORBIDDEN_SUFFIXES:
            errors.append(f'Private input or credential file must not be committed: {name}')
        if parts[:2] == ('data', 'raw') and name != 'data/raw/.gitkeep':
            errors.append(f'Raw data must not be committed: {name}')
        if any(part in {'node_modules', 'target', 'dist', '.idea', '__pycache__'} for part in parts):
            errors.append(f'Generated or machine-local content must not be committed: {name}')
        if path.name == '.env' or (path.name.startswith('.env.') and path.name != '.env.example'):
            errors.append(f'Local environment file must not be committed: {name}')
        if path.suffix.lower() in TEXT_SUFFIXES or path.name in {'.gitignore', '.gitattributes', '.editorconfig', '.env.example', 'CODEOWNERS'}:
            try:
                text = path.read_text(encoding='utf-8')
            except UnicodeDecodeError:
                errors.append(f'File is not UTF-8: {name}')
                continue
            text_count += 1
            if '\ufffd' in text:
                errors.append(f'Encoding replacement character found: {name}')
            # 这里只匹配明显私钥头，不是全面的密钥/个人数据检测；提交前仍需审查实际差异。
            if re.search(r'-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----', text):
                errors.append(f'Private key material found: {name}')
            if re.search(r'^(<{7}|={7}|>{7})(?:\s.*)?$', text, re.MULTILINE):
                errors.append(f'Unresolved conflict marker found: {name}')
            if path.suffix == '.md':
                markdown_links(path, text, errors)
    if all((ROOT / name).exists() for name in ['data/samples/users.sample.csv', 'data/samples/departments.sample.csv']):
        sample_data(errors)
    if errors:
        print('Repository checks failed:')
        for error in errors:
            print(' - ' + error)
        return 1
    print(f'Repository checks passed: {len(names)} files, {text_count} UTF-8 text files, local links and synthetic sample relationships checked.')
    print('Scope: repository structure and files only; permission tests, application builds and backend tests are separate checks.')
    return 0

if __name__ == '__main__':
    sys.exit(main())
