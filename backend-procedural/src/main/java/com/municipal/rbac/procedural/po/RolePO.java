package com.municipal.rbac.procedural.po;

public record RolePO(
    String id,
    String code,
    String name,
    String description,
    boolean enabled,
    boolean builtin,
    int permissionVersion) {}
