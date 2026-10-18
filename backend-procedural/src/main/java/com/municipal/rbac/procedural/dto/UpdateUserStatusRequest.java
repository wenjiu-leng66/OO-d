package com.municipal.rbac.procedural.dto;

import jakarta.validation.constraints.NotBlank;

/** 修改用户状态请求：status 只允许 active / inactive。 */
public record UpdateUserStatusRequest(
        @NotBlank(message = "状态不能为空") String status) {
}
