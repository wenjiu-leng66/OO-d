package com.municipal.rbac.procedural.vo;

import java.util.List;

/** VO（View Object）：可授权的最末级权限目录项。 */
public record PermissionItemVO(
        String code,
        String name,
        String systemId,
        String systemName,
        String moduleId,
        String moduleName,
        String resourceCode,
        String resourceName,
        String action,
        List<String> scopeOptions) {
}
