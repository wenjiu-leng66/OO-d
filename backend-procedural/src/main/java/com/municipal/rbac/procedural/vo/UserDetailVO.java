package com.municipal.rbac.procedural.vo;

import java.util.List;

/** VO（View Object）：用户详情，含电话、角色名与有效权限摘要。 */
public record UserDetailVO(
        String id,
        String name,
        String employeeNo,
        String departmentId,
        String departmentName,
        String branch,
        String position,
        String status,
        String accountType,
        String phone,
        List<String> roles,
        List<String> permissionCodes) {
}
