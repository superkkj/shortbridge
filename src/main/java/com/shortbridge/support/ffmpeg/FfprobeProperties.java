package com.shortbridge.support.ffmpeg;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shortbridge.ffmpeg")
public record FfprobeProperties(String ffprobePath) {

  public String resolvedPath() {
    return ffprobePath == null || ffprobePath.isBlank() ? "ffprobe" : ffprobePath;
  }
}
