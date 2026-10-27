package com.municipal.rbac.procedural.po;

/** 内部用户行模型，包含 passwordHash，绝不能直接作为 HTTP 响应返回。
 * roleVersion 用于角色分配集合，mustChangePassword 控制首次/重置后的业务访问边界。 */
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
