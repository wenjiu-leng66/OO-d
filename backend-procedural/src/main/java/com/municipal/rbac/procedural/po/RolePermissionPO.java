package com.municipal.rbac.procedural.po;

/** PO（Persistent Object）：对应 role_permissions 关联表一行（含授权范围 scope）。 */
public record RolePermissionPO(
        String roleId,
        String permissionCode,
        String scope) {
}
