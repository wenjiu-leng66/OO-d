package com.municipal.rbac.procedural.po;

/** PO（Persistent Object）：对应 departments 表一行。 */
public record DepartmentPO(
        String id,
        String name,
        String branch,
        String parentId,
        String managerId) {
}
