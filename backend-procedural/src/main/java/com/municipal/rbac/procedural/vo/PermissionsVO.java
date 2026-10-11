package com.municipal.rbac.procedural.vo;

import java.util.List;

/** VO（View Object）：当前用户有效权限，角色集合 + 去重权限码并集 + 范围。 */
public record PermissionsVO(
        String userId,
        List<String> roleIds,
        List<String> permissionCodes,
        List<Scope> scopes,
        int version) {

    public record Scope(String permissionCode, String scope) {
    }
}
