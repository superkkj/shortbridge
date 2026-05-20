package com.shortbridge.platform.socialaccount.domain;

public enum Platform {
  YOUTUBE,
  INSTAGRAM,
  TIKTOK;

  public String queueName() {
    return "publish." + name().toLowerCase();
  }
}
