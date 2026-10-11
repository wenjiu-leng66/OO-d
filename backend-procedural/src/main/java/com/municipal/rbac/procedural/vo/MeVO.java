package com.municipal.rbac.procedural.vo;

/** VO（View Object）：当前登录身份的可展示字段。 */
public record MeVO(
        String id,
        String name,
        String employeeNo,
        String departmentId,
        String departmentName,
        String accountType) {
}
