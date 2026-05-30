package com.shortbridge.platform.posttarget.domain;

import java.util.Set;

public enum PostTargetStatus {
  READY,
  QUEUED,
  LOCKED,
  UPLOADING,
  PROCESSING,
  PUBLISHED,
  FAILED_TEMPORARY,
  FAILED_PERMANENT,
  RETRY_WAIT,
  PRIVATE_LIMITED,
  BLOCKED_BY_QUOTA,
  BLOCKED_BY_CAPABILITY,
  RECONNECT_REQUIRED,
  CANCELED;

  public static final Set<PostTargetStatus> RETRYABLE =
      Set.of(FAILED_TEMPORARY, RETRY_WAIT, BLOCKED_BY_QUOTA, BLOCKED_BY_CAPABILITY, RECONNECT_REQUIRED);

  public static final Set<PostTargetStatus> TERMINAL =
      Set.of(PUBLISHED, FAILED_PERMANENT, PRIVATE_LIMITED, BLOCKED_BY_CAPABILITY, RECONNECT_REQUIRED, CANCELED);

  public boolean isRetryable() {
    return RETRYABLE.contains(this);
  }

  public boolean isTerminal() {
    return TERMINAL.contains(this);
  }
}
