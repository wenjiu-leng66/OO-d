"""Validate the single stage-one OpenAPI source, optionally compare live operations."""
import argparse
import json
from pathlib import Path
import sys
from urllib.request import urlopen
import yaml
from openapi_spec_validator import validate

ROOT = Path(__file__).resolve().parents[1]
METHODS = {"get", "post", "put", "patch", "delete", "options", "head"}

def operations(spec, prefix=""):
    return {(path.removeprefix(prefix), method) for path, item in spec["paths"].items()
            for method in item if method in METHODS}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", help="Optional running origin, without /api/v1")
    args = parser.parse_args()
    spec = yaml.safe_load((ROOT / "api/openapi.yaml").read_text(encoding="utf-8"))
    validate(spec)
    expected = operations(spec)
    print(f"OpenAPI valid: {len(spec['paths'])} paths, {len(expected)} operations")
    if args.base_url:
        with urlopen(args.base_url.rstrip("/") + "/v3/api-docs", timeout=30) as response:
            actual = operations(json.load(response), "/api/v1")
        missing, undocumented = sorted(expected - actual), sorted(actual - expected)
        if missing or undocumented:
            print(json.dumps({"missing": missing, "undocumented": undocumented}), file=sys.stderr)
            return 1
        print("Live backend methods and paths exactly match the API design")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
