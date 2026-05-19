package com.shortbridge.publish.dto;

import java.time.Instant;

public record PublishOutcome(
    ResultType resultType,
    String externalPostId,
    String externalPublishId,
    String errorCode,
    String errorMessage,
    String rawResponseJson,
    Instant nextRetryAt) {

  public enum ResultType {
    SUCCESS,
    FAILED_TEMPORARY,
    FAILED_PERMANENT,
    RECONNECT_REQUIRED,
    BLOCKED_BY_QUOTA,
    BLOCKED_BY_CAPABILITY,
    PRIVATE_LIMITED
  }

  public static PublishOutcome success(String externalPostId, String externalPublishId, String rawJson) {
    return new PublishOutcome(ResultType.SUCCESS, externalPostId, externalPublishId, null, null, rawJson, null);
  }

  public static PublishOutcome failedTemporary(String code, String message, Instant nextRetryAt) {
    return new PublishOutcome(ResultType.FAILED_TEMPORARY, null, null, code, message, null, nextRetryAt);
  }

  public static PublishOutcome failedPermanent(String code, String message) {
    return new PublishOutcome(ResultType.FAILED_PERMANENT, null, null, code, message, null, null);
  }

  public static PublishOutcome reconnectRequired(String message) {
    return new PublishOutcome(ResultType.RECONNECT_REQUIRED, null, null, "RECONNECT_REQUIRED", message, null, null);
  }

  public static PublishOutcome blockedByQuota(Instant nextRetryAt) {
    return new PublishOutcome(ResultType.BLOCKED_BY_QUOTA, null, null, "QUOTA_EXCEEDED", null, null, nextRetryAt);
  }

  public static PublishOutcome blockedByCapability(String message) {
    return new PublishOutcome(ResultType.BLOCKED_BY_CAPABILITY, null, null, "CAPABILITY_BLOCKED", message, null, null);
  }

  public static PublishOutcome privateLimited(String message) {
    return new PublishOutcome(ResultType.PRIVATE_LIMITED, null, null, "PRIVATE_LIMITED", message, null, null);
  }
}
