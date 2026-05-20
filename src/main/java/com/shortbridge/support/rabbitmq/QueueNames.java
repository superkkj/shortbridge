package com.shortbridge.support.rabbitmq;

/**
 * Queue 이름 상수. 각 상수는 {@code Platform.X.queueName()} 과 일치한다 — annotation 의
 * compile-time constant 요구사항 때문에 별도로 둔다. 새 platform 추가 시 양쪽 모두 추가하라.
 */
public final class QueueNames {

  public static final String YOUTUBE = "publish.youtube";
  public static final String INSTAGRAM = "publish.instagram";
  public static final String TIKTOK = "publish.tiktok";

  public static final String DLQ_SUFFIX = ".dlq";

  private QueueNames() {}

  public static String dlqOf(String queue) {
    return queue + DLQ_SUFFIX;
  }
}
