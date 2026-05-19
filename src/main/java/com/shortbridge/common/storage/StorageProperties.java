package com.shortbridge.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shortbridge.storage")
public record StorageProperties(String type, LocalProperties local, S3Properties s3) {

  public record LocalProperties(String baseDir, String publicBaseUrl) {}

  public record S3Properties(
      String endpoint,
      String region,
      String bucket,
      String accessKey,
      String secretKey,
      boolean pathStyleAccess,
      long presignedUrlTtlSeconds) {}
}
