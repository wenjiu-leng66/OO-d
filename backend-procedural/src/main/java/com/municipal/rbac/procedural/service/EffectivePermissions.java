package com.municipal.rbac.procedural.service;

import java.util.List;
import java.util.Map;

/**
 * RBAC0 有效权限快照：启用的直接角色 ID、去重权限码、每个权限码的最大数据范围。
 * 不包含继承、会话激活选择或后续阶段约束；快照只供本次请求使用，不作为长期授权缓存。
 */
public record EffectivePermissions(
    List<String> roleIds, List<String> permissionCodes, Map<String, String> scopes) {}
