package com.municipal.rbac.procedural.service;

import java.util.List;
import java.util.Map;

/**
 * 某用户的有效权限快照：角色集合、去重权限码并集、权限码到范围（all/self）的映射。
 */
public record EffectivePermissions(
        List<String> roleIds,
        List<String> permissionCodes,
        Map<String, String> scopes) {
}
