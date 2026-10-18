package com.municipal.rbac.procedural.po;

public record UserPO(
    String id,
    String username,
    String name,
    String departmentId,
    String position,
    String phone,
    String email,
    boolean enabled,
    String passwordHash,
    boolean mustChangePassword,
    int roleVersion) {}
