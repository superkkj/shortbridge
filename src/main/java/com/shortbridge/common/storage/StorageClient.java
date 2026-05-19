package com.shortbridge.common.storage;

import java.io.InputStream;
import java.time.Duration;

public interface StorageClient {

  StoredObject store(String key, String contentType, long contentLength, InputStream content);

  InputStream open(String key);

  void delete(String key);

  String publicUrl(String key);

  String presignedGetUrl(String key, Duration ttl);
}
