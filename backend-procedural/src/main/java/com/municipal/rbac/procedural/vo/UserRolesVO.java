package com.municipal.rbac.procedural.vo;

import java.util.List;

/** VO（View Object）：用户完整角色集合与集合版本。 */
public record UserRolesVO(String userId, List<String> roleIds, int version) {
}
