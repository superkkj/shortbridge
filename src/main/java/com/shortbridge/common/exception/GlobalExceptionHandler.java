package com.shortbridge.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ShortBridgeException.class)
  public ResponseEntity<ErrorResponse> handleShortBridge(
      ShortBridgeException ex, HttpServletRequest request) {
    log.warn("ShortBridgeException: code={} message={}", ex.getErrorCode().getCode(), ex.getMessage());
    ErrorCode errorCode = ex.getErrorCode();
    return ResponseEntity.status(errorCode.getStatus())
        .body(ErrorResponse.of(errorCode, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<ErrorResponse.FieldError> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
            .toList();
    return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
        .body(ErrorResponse.of(
            ErrorCode.VALIDATION_FAILED,
            ErrorCode.VALIDATION_FAILED.getMessageTemplate(),
            request.getRequestURI(),
            fieldErrors));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleUploadSize(
      MaxUploadSizeExceededException ex, HttpServletRequest request) {
    return ResponseEntity.status(ErrorCode.VIDEO_SIZE_EXCEEDED.getStatus())
        .body(ErrorResponse.of(
            ErrorCode.VIDEO_SIZE_EXCEEDED,
            ErrorCode.VIDEO_SIZE_EXCEEDED.format(ex.getMessage()),
            request.getRequestURI()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    return ResponseEntity.status(ErrorCode.FORBIDDEN.getStatus())
        .body(ErrorResponse.of(
            ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessageTemplate(), request.getRequestURI()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleAny(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception: path={}", request.getRequestURI(), ex);
    return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
        .body(ErrorResponse.of(
            ErrorCode.INTERNAL_ERROR,
            ErrorCode.INTERNAL_ERROR.getMessageTemplate(),
            request.getRequestURI()));
  }
}
