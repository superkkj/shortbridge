package com.shortbridge.common.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

  @Bean
  public StorageClient storageClient(StorageProperties props) {
    if ("s3".equalsIgnoreCase(props.type())) {
      return new S3StorageClient(props.s3());
    }
    return new LocalStorageClient(props.local());
  }
}
