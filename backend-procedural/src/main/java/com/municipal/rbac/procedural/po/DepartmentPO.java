package com.municipal.rbac.procedural.po;

public record DepartmentPO(
    String id, String name, String shortName, String type, String parentId, String managerId) {}
