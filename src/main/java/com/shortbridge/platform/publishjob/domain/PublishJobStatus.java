package com.shortbridge.platform.publishjob.domain;

public enum PublishJobStatus {
  QUEUED,
  PROCESSING,
  SUCCEEDED,
  FAILED,
  RETRY_WAIT,
  DEAD_LETTER
}
