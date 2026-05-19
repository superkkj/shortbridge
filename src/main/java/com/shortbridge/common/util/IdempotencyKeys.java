package com.shortbridge.common.util;

import java.util.UUID;

public final class IdempotencyKeys {

  private IdempotencyKeys() {}

  public static String forPostTarget(UUID postId, String platform) {
    return postId + ":" + platform;
  }
}
