package com.municipal.rbac.procedural.mapper;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** 公共 ID 边界：拒绝过长、溢出、非正数及非规范十进制输入，避免 SQL 类型错误。 */
class IdsTest {
  @Test
  void positiveIntEdgesAreAccepted() {
    assertTrue(Ids.valid("1"));
    assertTrue(Ids.valid("2147483647"));
  }

  @Test
  void malformedAndOverflowingIdsAreRejectedWithoutThrowing() {
    for (String value : new String[] {null, "", "0", "-1", "+1", "01", "1.0", " 1", "2147483648", "999999999999999999999999999999"}) {
      assertFalse(Ids.valid(value), "Unexpected valid ID: " + value);
    }
  }
}
