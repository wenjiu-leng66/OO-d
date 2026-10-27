package com.municipal.rbac.procedural.po;

/** 角色表行模型。permissionVersion 用于整组授权的乐观并发控制，builtin 表示额外删除保护。 */
public record RolePO(
    String id,
    String code,
    String name,
    String description,
    boolean enabled,
    boolean builtin,
    int permissionVersion) {}
