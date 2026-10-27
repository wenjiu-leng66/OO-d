package com.municipal.rbac.procedural.mapper;

/**
 * 权威数据库 ID 为正 INT，对外统一序列化成十进制字符串。
 * 先限制长度再 parseLong，避免任意长 URL 参数溢出或落入 JDBC 类型转换异常。
 */
public final class Ids {
  private Ids() {}

  public static boolean valid(String id) {
    return id != null && id.matches("[1-9][0-9]{0,9}") && Long.parseLong(id) <= Integer.MAX_VALUE;
  }
}
