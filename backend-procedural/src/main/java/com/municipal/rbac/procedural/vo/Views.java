package com.municipal.rbac.procedural.vo;

import java.util.*;

/**
 * Explicit response construction: database rows and password hashes are never serialized directly.
 */
public final class Views {
  private Views() {}

  public static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

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
