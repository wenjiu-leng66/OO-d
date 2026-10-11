package com.municipal.rbac.procedural.vo;

/** VO（View Object）：资源目录节点（system/module/function）。 */
public record ResourceItemVO(
        String id,
        String name,
        String type,
        String parentId,
        boolean hasChildren) {
}
