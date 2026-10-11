package com.municipal.rbac.procedural.vo;

import java.util.List;

/** VO（View Object）：角色列表项，含直接权限码与授权集合版本。 */
public record RoleVO(
        String id,
        String code,
        String name,
        String description,
        List<String> permissionCodes,
        int version) {
}
