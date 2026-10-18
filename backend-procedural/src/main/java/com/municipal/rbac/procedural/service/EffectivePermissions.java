package com.municipal.rbac.procedural.service;

import java.util.List;
import java.util.Map;

/** RBAC0 union of enabled direct roles; no inheritance or later-stage constraints. */
public record EffectivePermissions(
    List<String> roleIds, List<String> permissionCodes, Map<String, String> scopes) {}
