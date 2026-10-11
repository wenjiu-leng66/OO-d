package com.municipal.rbac.procedural.vo;

import java.util.List;

/** VO（View Object）：登录成功响应，当前身份 + 有效权限摘要。 */
public record LoginVO(UserSummary user, List<String> permissionCodes) {

    public record UserSummary(String id, String name, String employeeNo, String departmentName) {
    }
}
