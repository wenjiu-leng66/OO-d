package com.municipal.rbac.procedural.po;

/** 组织表只读行模型；parentId/managerId 为外键 ID，名称仅作展示，不能用来判定组织身份。 */
public record DepartmentPO(
    String id, String name, String shortName, String type, String parentId, String managerId) {}
