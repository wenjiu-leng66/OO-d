package com.municipal.rbac.procedural.po;

/**
 * PO（Persistent Object）：对应 roles 表一行。
 * permissionVersion 为角色权限集合的乐观锁版本，仅供后端逻辑使用。
 */
public record RolePO(
        String id,
        String code,
        String name,
        String description,
        boolean enabled,
        int permissionVersion) {
}
