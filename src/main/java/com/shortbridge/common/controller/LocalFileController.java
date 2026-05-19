package com.shortbridge.common.controller;

import com.shortbridge.common.storage.StorageClient;
import com.shortbridge.common.storage.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/files")
@ConditionalOnProperty(prefix = "shortbridge.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalFileController {

  private final StorageClient storageClient;
  private final StorageProperties storageProperties;

  public LocalFileController(StorageClient storageClient, StorageProperties storageProperties) {
    this.storageClient = storageClient;
    this.storageProperties = storageProperties;
  }

  @GetMapping("/**")
  public ResponseEntity<InputStreamResource> serve(jakarta.servlet.http.HttpServletRequest request)
      throws IOException {
    String path = request.getRequestURI().substring("/files/".length());
    InputStream stream = storageClient.open(path);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/octet-stream"))
        .body(new InputStreamResource(stream));
  }

  @SuppressWarnings("unused")
  private StorageProperties props() {
    return storageProperties;
  }

  @SuppressWarnings("unused")
  @GetMapping("/by-key/{key:.+}")
  public ResponseEntity<InputStreamResource> serveByKey(@PathVariable String key) {
    InputStream stream = storageClient.open(key);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/octet-stream"))
        .body(new InputStreamResource(stream));
  }
}
