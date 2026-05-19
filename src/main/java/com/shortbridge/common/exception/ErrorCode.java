package com.shortbridge.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
  // 400
  INVALID_REQUEST(400, "C001", "잘못된 요청입니다: %s"),
  VALIDATION_FAILED(400, "C002", "입력값 검증에 실패했습니다"),

  // 401 / 403
  UNAUTHENTICATED(401, "A001", "인증이 필요합니다"),
  FORBIDDEN(403, "A002", "권한이 없습니다"),

  // 404
  USER_NOT_FOUND(404, "U001", "사용자를 찾을 수 없습니다: %s"),
  LOGIN_ACCOUNT_NOT_FOUND(404, "L001", "로그인 계정을 찾을 수 없습니다: %s"),
  SOCIAL_ACCOUNT_NOT_FOUND(404, "S001", "플랫폼 계정을 찾을 수 없습니다: %s"),
  SOCIAL_ACCOUNT_NOT_CONNECTED(409, "S002", "연결되지 않은 플랫폼입니다: %s"),
  VIDEO_NOT_FOUND(404, "V001", "영상을 찾을 수 없습니다: %s"),
  POST_NOT_FOUND(404, "P001", "게시물을 찾을 수 없습니다: %s"),
  POST_TARGET_NOT_FOUND(404, "PT001", "발행 대상을 찾을 수 없습니다: %s"),

  // 409 conflict
  DUPLICATE_LOGIN_ACCOUNT(409, "L002", "이미 등록된 로그인 계정입니다: %s"),
  POST_ALREADY_PUBLISHED(409, "P002", "이미 발행된 게시물입니다: %s"),
  POST_NOT_RETRYABLE(409, "PT002", "재시도 가능한 상태가 아닙니다: %s"),

  // 422 - 영상 검증
  VIDEO_INVALID_FORMAT(422, "V101", "지원하지 않는 영상 포맷입니다: %s"),
  VIDEO_DURATION_EXCEEDED(422, "V102", "영상 길이가 제한을 초과했습니다: %s"),
  VIDEO_SIZE_EXCEEDED(422, "V103", "영상 크기가 제한을 초과했습니다: %s"),
  VIDEO_PROBE_FAILED(422, "V104", "영상 메타데이터 분석에 실패했습니다"),

  // 500
  INTERNAL_ERROR(500, "E001", "내부 오류가 발생했습니다"),
  STORAGE_ERROR(500, "E002", "스토리지 처리 중 오류가 발생했습니다"),
  TOKEN_CIPHER_ERROR(500, "E003", "토큰 암복호화 처리 중 오류가 발생했습니다"),
  JSON_PROCESSING_ERROR(500, "E004", "JSON 처리에 실패했습니다"),
  EXTERNAL_PLATFORM_ERROR(502, "E005", "외부 플랫폼 호출에 실패했습니다: %s");

  private final int status;
  private final String code;
  private final String messageTemplate;

  ErrorCode(int status, String code, String messageTemplate) {
    this.status = status;
    this.code = code;
    this.messageTemplate = messageTemplate;
  }

  public String format(Object... args) {
    if (args == null || args.length == 0) {
      return messageTemplate;
    }
    return messageTemplate.formatted(args);
  }
}
