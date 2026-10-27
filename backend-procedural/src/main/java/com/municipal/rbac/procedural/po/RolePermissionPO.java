package com.municipal.rbac.procedural.po;

/** 单个角色的原始清单授权，scope 属于该权限码本身；有效并集在 AuthorizationService 中计算。 */
public record RolePermissionPO(String roleId, String permissionCode, String scope) {}
