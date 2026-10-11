package com.municipal.rbac.procedural.po;

/** PO（Persistent Object）：对应 user_roles 关联表一行。 */
public record UserRolePO(
        String userId,
        String roleId) {
}
