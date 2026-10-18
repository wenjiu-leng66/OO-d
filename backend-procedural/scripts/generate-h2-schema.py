"""Translate the authoritative MySQL migration into the LOCAL H2 test dialect.

Production always executes database/migrations/V001__create_core_tables.sql.
This tool changes dialect constructs only, preserving columns and constraints.
"""
import argparse
from pathlib import Path
import re


def generated_schema(source: str) -> str:
    sql = re.sub(r"COMMENT\s+'(?:[^']|'')*'", '', source)
    sql = re.sub(r'\) ENGINE\s*=\s*InnoDB[^;]*;', ');', sql)
    sql = re.sub(r'^\s*KEY \w+ \([^\n]+\),?\n', '', sql, flags=re.M)
    sql = re.sub(r'UNIQUE KEY \w+', 'UNIQUE', sql)
    sql = sql.replace(' ON UPDATE CURRENT_TIMESTAMP', '')
    sql = sql.replace(
        "ELT(MAX(FIELD(rp.data_scope, 'SELF', 'DEPT', 'ALL')), 'SELF', 'DEPT', 'ALL')",
        "CASE MAX(CASE rp.data_scope WHEN 'ALL' THEN 3 WHEN 'DEPT' THEN 2 ELSE 1 END) WHEN 3 THEN 'ALL' WHEN 2 THEN 'DEPT' ELSE 'SELF' END",
    )
    sql = sql.replace('CREATE TABLE ', 'CREATE TABLE IF NOT EXISTS ')
    sql = sql.replace('CREATE VIEW ', 'CREATE OR REPLACE VIEW ')
    sql = sql.replace('ADD CONSTRAINT fk_dept_manager', 'ADD CONSTRAINT IF NOT EXISTS fk_dept_manager')
    return '-- LOCAL H2 DIALECT ONLY. Mechanically adapted from database/migrations/V001__create_core_tables.sql. Production executes that authoritative migration.\n' + sql


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Fail on schema drift without changing files')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    source = root / 'database/migrations/V001__create_core_tables.sql'
    target = root / 'backend-procedural/src/main/resources/schema.sql'
    expected = generated_schema(source.read_text(encoding='utf-8'))
    if args.check:
        if target.read_text(encoding='utf-8') != expected:
            raise SystemExit('H2 schema drift: regenerate with backend-procedural/scripts/generate-h2-schema.py')
    else:
        target.write_text(expected, encoding='utf-8')
    print('H2 dialect matches the authoritative MySQL migration.')


if __name__ == '__main__':
    main()
