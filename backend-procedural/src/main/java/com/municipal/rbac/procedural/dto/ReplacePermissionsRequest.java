package com.municipal.rbac.procedural.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 替换角色直接权限集合：完整集合 + 版本。空集合表示撤销全部直接权限。 */
public record ReplacePermissionsRequest(
        @NotNull(message = "permissionCodes 不能为空") List<String> permissionCodes,
        @NotNull(message = "version 不能为空") Integer version) {
}
