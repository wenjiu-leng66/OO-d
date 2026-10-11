package com.municipal.rbac.procedural.vo;

import java.util.Map;

/**
 * VO（View Object）：统一错误包络，与接口草案一致：
 * { "error": { "code", "message", "requestId", "details" } }
 */
public record ErrorVO(ErrorBody error) {

    public record ErrorBody(String code, String message, String requestId, Map<String, Object> details) {
    }
}
