package com.municipal.rbac.procedural.mapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbac.procedural.po.DepartmentPO;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** 隔离测试子树算法，findAll 返回值被替换；SQL、列映射和外键不由这些 mock 断言证明。 */
class DepartmentMapperTest {
  DepartmentPO department(String id, String parent) {
    return new DepartmentPO(id, "Synthetic", "S", "HQ_DEPT", parent, null);
  }

  DepartmentMapper mapper(List<DepartmentPO> rows) {
    var mapper = spy(new DepartmentMapper(mock(JdbcTemplate.class)));
    doReturn(rows).when(mapper).findAll();
    return mapper;
  }

  @Test
  void subtreeContainsSelfAndAllDescendantsRegardlessOfRowOrder() {
    var mapper = mapper(List.of(department("12", "11"), department("20", null), department("10", null), department("11", "10")));
    assertEquals(Set.of("10", "11", "12"), mapper.subtree("10"));
    assertEquals(Set.of("12"), mapper.subtree("12"));
  }

  @Test
  void missingOrNullRootReturnsEmptyWithoutGrantingOtherDepartments() {
    var mapper = mapper(List.of(department("10", null)));
    assertTrue(mapper.subtree("999").isEmpty());
    assertTrue(mapper.subtree(null).isEmpty());
  }

  @Test
  void malformedCycleDoesNotMakeSubtreeExpansionLoopForever() {
    var mapper = mapper(List.of(department("10", "11"), department("11", "10")));
    assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> assertEquals(Set.of("10", "11"), mapper.subtree("10")));
  }
}
