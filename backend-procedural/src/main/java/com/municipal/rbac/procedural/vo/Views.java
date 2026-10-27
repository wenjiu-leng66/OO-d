package com.municipal.rbac.procedural.vo;

import java.util.*;

/**
 * 显式构造接口响应，数据库行及密码摘要不能直接序列化。
 * 分页助手接收的必须是已经完成授权过滤的集合，否则 total 会泄露范围外记录数量。
 */
public final class Views {
  private Views() {}

  /**
   * 供内部固定字段组装使用，参数按键/值成对传入；LinkedHashMap 保留响应字段的可读顺序。
   */
  public static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  /**
   * 分页边界由 UserService.paging 提前验证，越过末页返回空 items；映射只应用到本页条目。
   * 该助手是内存分页，不承诺 SQL LIMIT/OFFSET 或数据库级范围过滤。
   */
  public static <T> Map<String, Object> page(
      List<T> values, int page, int pageSize, java.util.function.Function<T, ?> mapper) {
    int from = Math.min(values.size(), (page - 1) * pageSize),
        to = Math.min(values.size(), from + pageSize);
    return map(
        "items",
        values.subList(from, to).stream().map(mapper).toList(),
        "page",
        page,
        "pageSize",
        pageSize,
        "total",
        values.size());
  }

  public static <T> Map<String, Object> page(List<T> values, int page, int pageSize) {
    int from = Math.min(values.size(), (page - 1) * pageSize),
        to = Math.min(values.size(), from + pageSize);
    return map(
        "items",
        values.subList(from, to),
        "page",
        page,
        "pageSize",
        pageSize,
        "total",
        values.size());
  }
}
