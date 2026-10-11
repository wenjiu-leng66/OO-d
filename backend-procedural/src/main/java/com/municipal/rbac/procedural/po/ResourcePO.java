package com.municipal.rbac.procedural.po;

/** PO（Persistent Object）：对应 resources 表一行。type 取值 system/module/function。 */
public record ResourcePO(
        String id,
        String name,
        String type,
        String parentId) {
}
