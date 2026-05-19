package com.shortbridge.platform.video;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shortbridge.video")
public record VideoProperties(
    int maxDurationSeconds,
    long maxFileSizeBytes,
    List<String> allowedMimeTypes,
    List<String> allowedVideoCodecs,
    List<String> allowedAudioCodecs) {}
