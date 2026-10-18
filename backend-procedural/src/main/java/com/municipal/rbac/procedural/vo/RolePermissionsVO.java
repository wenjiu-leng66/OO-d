package com.municipal.rbac.procedural.vo;

import java.util.List;

/** VO（View Object）：角色完整直接权限集合与集合版本。 */
public record RolePermissionsVO(String roleId, List<String> permissionCodes, int version) {
}
