package com.shortbridge.common.exception;

import lombok.Getter;

@Getter
public class ShortBridgeException extends RuntimeException {

  private final ErrorCode errorCode;

  public ShortBridgeException(ErrorCode errorCode) {
    super(errorCode.getMessageTemplate());
    this.errorCode = errorCode;
  }

  public ShortBridgeException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public ShortBridgeException(ErrorCode errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  public static ShortBridgeException of(ErrorCode errorCode, Object... args) {
    return new ShortBridgeException(errorCode, errorCode.format(args));
  }

  public static ShortBridgeException of(ErrorCode errorCode, Throwable cause, Object... args) {
    return new ShortBridgeException(errorCode, errorCode.format(args), cause);
  }
}
