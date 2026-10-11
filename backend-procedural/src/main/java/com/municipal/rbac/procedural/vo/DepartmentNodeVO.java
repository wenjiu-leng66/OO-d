package com.municipal.rbac.procedural.vo;

import java.util.List;

/** VO（View Object）：部门树节点。manager 为负责人姓名（展示用）。 */
public record DepartmentNodeVO(
        String id,
        String name,
        String manager,
        String branch,
        String parentId,
        String parentName,
        int employeeCount,
        List<DepartmentNodeVO> children) {
}
