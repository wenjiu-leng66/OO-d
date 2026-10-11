package com.municipal.rbac.procedural.po;

/**
 * PO（Persistent Object）：对应 users 表一行。
 * passwordHash / roleVersion 仅供后端逻辑使用，不直接返回给前端。
 */
public record UserPO(
        String id,
        String name,
        String employeeNo,
        String phone,
        String departmentId,
        String position,
        String accountType,
        String status,
        String passwordHash,
        int roleVersion) {
}
