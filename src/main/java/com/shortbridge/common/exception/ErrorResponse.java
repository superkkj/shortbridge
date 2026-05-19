package com.shortbridge.common.exception;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    List<FieldError> errors) {

  public record FieldError(String field, String message, Object rejectedValue) {}

  public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
    return new ErrorResponse(Instant.now(), errorCode.getStatus(), errorCode.getCode(), message, path, List.of());
  }

  public static ErrorResponse of(
      ErrorCode errorCode, String message, String path, List<FieldError> errors) {
    return new ErrorResponse(Instant.now(), errorCode.getStatus(), errorCode.getCode(), message, path, errors);
  }
}
