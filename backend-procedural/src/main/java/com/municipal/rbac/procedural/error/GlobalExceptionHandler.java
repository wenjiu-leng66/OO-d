package com.municipal.rbac.procedural.error;

import com.municipal.rbac.procedural.vo.ErrorVO;
import java.util.*;
import org.slf4j.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorVO> api(ApiException e) {
    return ResponseEntity.status(e.status()).body(body(e.code(), e.getMessage(), e.details()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ErrorVO> validation(Exception e) {
    return ResponseEntity.badRequest()
        .body(body("VALIDATION", "Invalid request parameters or JSON body.", null));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorVO> conflict(DataIntegrityViolationException e) {
    String message =
        Optional.ofNullable(e.getMostSpecificCause().getMessage())
            .orElse("")
            .toLowerCase(Locale.ROOT);
    String code =
        message.contains("uk_user_username")
            ? "DUPLICATE_USERNAME"
            : message.contains("uk_user_phone")
                ? "DUPLICATE_PHONE"
                : message.contains("uk_role_code") ? "DUPLICATE_ROLE_CODE" : "DATA_CONFLICT";
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(body(code, "Conflicting data. Refresh and retry.", null));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorVO> notFound(NoResourceFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(body("NOT_FOUND", "Endpoint not found.", null));
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorVO> method(Exception e) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(body("METHOD_NOT_ALLOWED", "Method not allowed.", null));
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorVO> media(Exception e) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(body("UNSUPPORTED_MEDIA_TYPE", "Unsupported request content type.", null));
  }

  @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorVO> missing(Exception e) {
    return ResponseEntity.badRequest().body(body("VALIDATION", "Missing request parameter.", null));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorVO> unexpected(Exception e) {
    log.error("Unhandled request exception", e);
    return ResponseEntity.internalServerError()
        .body(body("INTERNAL_ERROR", "Internal server error.", null));
  }

  private ErrorVO body(String code, String message, Map<String, Object> details) {
    return new ErrorVO(
        new ErrorVO.ErrorBody(
            code,
            message,
            UUID.randomUUID().toString().replace("-", "").substring(0, 12),
            details));
  }
}
