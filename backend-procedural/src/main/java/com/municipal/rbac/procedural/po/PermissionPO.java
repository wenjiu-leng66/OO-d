package com.municipal.rbac.procedural.po;

/**
 * PO（Persistent Object）：对应 permissions 表一行。
 * scopeOptions 为建表时保存的逗号分隔字符串（如 "all,self"），
 * 拆分为列表属于展示层转换，由 Service 组装 VO 时完成。
 */
public record PermissionPO(
        String code,
        String name,
        String systemId,
        String systemName,
        String moduleId,
        String moduleName,
        String resourceCode,
        String resourceName,
        String action,
        String scopeOptions) {
}
