package com.municipal.rbac.procedural.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建用户请求：name/employeeNo/departmentId/accountType/initialPassword 必填；
 * phone 与 position 可选；roleIds 缺省视为空集合。
 */
public record CreateUserRequest(
        @NotBlank(message = "姓名不能为空") String name,
        @NotBlank(message = "员工号不能为空") String employeeNo,
        @NotBlank(message = "部门不能为空") String departmentId,
        @NotBlank(message = "账号类型不能为空") String accountType,
        @NotBlank(message = "初始密码不能为空")
        @Size(min = 6, max = 64, message = "初始密码长度需在 6-64 位之间") String initialPassword,
        String phone,
        String position,
        List<String> roleIds) {
}
