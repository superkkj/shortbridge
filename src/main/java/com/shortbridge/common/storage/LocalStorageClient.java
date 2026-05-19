package com.shortbridge.common.storage;

import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalStorageClient implements StorageClient {

  private final Path baseDir;
  private final String publicBaseUrl;

  public LocalStorageClient(StorageProperties.LocalProperties props) {
    this.baseDir = Path.of(props.baseDir()).toAbsolutePath().normalize();
    this.publicBaseUrl = trimTrailingSlash(props.publicBaseUrl());
    try {
      Files.createDirectories(baseDir);
    } catch (IOException e) {
      throw ShortBridgeException.of(ErrorCode.STORAGE_ERROR, e);
    }
  }

  @Override
  public StoredObject store(String key, String contentType, long contentLength, InputStream content) {
    try {
      Path target = baseDir.resolve(key).normalize();
      if (!target.startsWith(baseDir)) {
        throw ShortBridgeException.of(ErrorCode.STORAGE_ERROR);
      }
      Files.createDirectories(target.getParent());
      long copied = Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
      return new StoredObject(key, copied, contentType, null);
    } catch (IOException e) {
      throw ShortBridgeException.of(ErrorCode.STORAGE_ERROR, e);
    }
  }

  @Override
  public InputStream open(String key) {
    try {
      return Files.newInputStream(resolve(key));
    } catch (IOException e) {
      throw ShortBridgeException.of(ErrorCode.STORAGE_ERROR, e);
    }
  }

  @Override
  public void delete(String key) {
    try {
      Files.deleteIfExists(resolve(key));
    } catch (IOException e) {
      log.warn("Local storage delete failed: key={}", key, e);
    }
  }

  @Override
  public String publicUrl(String key) {
    return publicBaseUrl + "/" + key;
  }

  @Override
  public String presignedGetUrl(String key, Duration ttl) {
    return publicUrl(key);
  }

  private Path resolve(String key) {
    Path target = baseDir.resolve(key).normalize();
    if (!target.startsWith(baseDir)) {
      throw ShortBridgeException.of(ErrorCode.STORAGE_ERROR);
    }
    return target;
  }

  private static String trimTrailingSlash(String value) {
    if (value == null || value.isEmpty()) return "";
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
