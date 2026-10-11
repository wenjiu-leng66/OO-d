package com.municipal.rbac.procedural.error;

import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * 带错误码的业务异常，由 GlobalExceptionHandler 统一转换为
 * { error: { code, message, requestId, details } } 包络。
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, null);
    }

    public static ApiException badRequest(String code, String message, Map<String, ?> details) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, toObjectMap(details));
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message, null);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message, null);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message, null);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message, null);
    }

    public static ApiException conflict(String code, String message, Map<String, ?> details) {
        return new ApiException(HttpStatus.CONFLICT, code, message, toObjectMap(details));
    }

    private static Map<String, Object> toObjectMap(Map<String, ?> details) {
        if (details == null) {
            return null;
        }
        return new HashMap<>(details);
    }
}
