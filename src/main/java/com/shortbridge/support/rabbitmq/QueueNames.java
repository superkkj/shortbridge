package com.shortbridge.support.rabbitmq;

public final class QueueNames {

  public static final String YOUTUBE = "publish.youtube";
  public static final String INSTAGRAM = "publish.instagram";
  public static final String TIKTOK = "publish.tiktok";

  public static final String DLQ_SUFFIX = ".dlq";

  private QueueNames() {}

  public static String dlqOf(String queue) {
    return queue + DLQ_SUFFIX;
  }

  public static String forPlatform(String platform) {
    return switch (platform.toUpperCase()) {
      case "YOUTUBE" -> YOUTUBE;
      case "INSTAGRAM" -> INSTAGRAM;
      case "TIKTOK" -> TIKTOK;
      default -> throw new IllegalArgumentException("unknown platform: " + platform);
    };
  }
}
