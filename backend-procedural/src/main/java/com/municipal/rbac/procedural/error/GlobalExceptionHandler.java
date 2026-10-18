package com.municipal.rbac.procedural.error;

import com.municipal.rbac.procedural.vo.ErrorVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorVO> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(body(ex.code(), ex.getMessage(), ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorVO> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(body("INVALID_ARGUMENT", "请求参数校验失败。", null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorVO> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(body("INVALID_ARGUMENT", "请求体格式错误。", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorVO> handleUnexpected(Exception ex) {
        log.error("未处理的异常", ex);
        return ResponseEntity.internalServerError().body(body("INTERNAL_ERROR", "服务器内部错误。", null));
    }

    private ErrorVO body(String code, String message, java.util.Map<String, Object> details) {
        return new ErrorVO(new ErrorVO.ErrorBody(code, message, requestId(), details));
    }

    private String requestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
