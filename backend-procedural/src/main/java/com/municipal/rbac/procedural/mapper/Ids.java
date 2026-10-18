package com.municipal.rbac.procedural.mapper;

/** IDs are positive INT in the authoritative database, serialized as decimal strings. */
public final class Ids {
  private Ids() {}

  public static boolean valid(String id) {
    return id != null && id.matches("[1-9][0-9]{0,9}") && Long.parseLong(id) <= Integer.MAX_VALUE;
  }
}
