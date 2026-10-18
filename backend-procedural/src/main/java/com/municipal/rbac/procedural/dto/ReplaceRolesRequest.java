package com.municipal.rbac.procedural.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 替换用户角色集合：完整集合 + 版本。空集合表示撤销全部角色。 */
public record ReplaceRolesRequest(
        @NotNull(message = "roleIds 不能为空") List<String> roleIds,
        @NotNull(message = "version 不能为空") Integer version) {
}
