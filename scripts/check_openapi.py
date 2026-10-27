"""校验一期权威 OpenAPI 契约，可选与运行后端的方法/路径集合双向比较。"""
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
    """只抽取 (路径, HTTP方法)，排除 parameters 等 Path Item 元数据；前缀只去掉一次。"""
    return {(path.removeprefix(prefix), method) for path, item in spec["paths"].items()
            for method in item if method in METHODS}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", help="Optional running origin, without /api/v1")
    args = parser.parse_args()
    spec = yaml.safe_load((ROOT / "api/openapi.yaml").read_text(encoding="utf-8"))
    # 静态校验处理 OpenAPI 结构及引用，不执行任何业务请求。
    validate(spec)
    expected = operations(spec)
    print(f"OpenAPI valid: {len(spec['paths'])} paths, {len(expected)} operations")
    if args.base_url:
        with urlopen(args.base_url.rstrip("/") + "/v3/api-docs", timeout=30) as response:
            # 静态契约通过 servers 声明 /api/v1，运行规范把它放在 paths 中，先统一比较口径。
            actual = operations(json.load(response), "/api/v1")
        # 这是双向操作覆盖检查，不证明参数/响应 schema、状态码及鉴权语义等价；后者需业务测试。
        missing, undocumented = sorted(expected - actual), sorted(actual - expected)
        if missing or undocumented:
            print(json.dumps({"missing": missing, "undocumented": undocumented}), file=sys.stderr)
            return 1
        print("Live backend methods and paths exactly match the API design")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
