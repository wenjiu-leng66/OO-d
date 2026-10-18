package com.municipal.rbac.procedural.vo;

/** VO（View Object）：用户列表项（不展开角色与权限）。 */
public record UserVO(
        String id,
        String name,
        String employeeNo,
        String departmentId,
        String departmentName,
        String branch,
        String position,
        String status,
        String accountType) {
}
