package com.shortbridge.publish.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublisherHttp {

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public HttpRequest.Builder bearer(String url, String accessToken) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url));
    if (accessToken != null && !accessToken.isBlank()) {
      builder.header("Authorization", "Bearer " + accessToken);
    }
    return builder;
  }

  public HttpRequest.Builder jsonBearer(String url, String accessToken) {
    return bearer(url, accessToken).header("Content-Type", "application/json; charset=UTF-8");
  }

  public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  public JsonNode postJson(String url, String accessToken, Object body, String opName) {
    try {
      String json = objectMapper.writeValueAsString(body);
      HttpRequest request =
          jsonBearer(url, accessToken).POST(HttpRequest.BodyPublishers.ofString(json)).build();
      return executeForJson(request, opName);
    } catch (JsonProcessingException e) {
      log.warn("{} request serialization failed", opName, e);
      return null;
    }
  }

  public JsonNode postEmpty(String url, String accessToken, String opName) {
    HttpRequest request = jsonBearer(url, accessToken).POST(HttpRequest.BodyPublishers.noBody()).build();
    return executeForJson(request, opName);
  }

  public JsonNode parse(String body) throws IOException {
    return objectMapper.readTree(body);
  }

  public String writeJson(Object body) throws JsonProcessingException {
    return objectMapper.writeValueAsString(body);
  }

  public HttpRequest.Builder uploadBuilder(String url, String contentType) {
    return HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofMinutes(5))
        .header("Content-Type", contentType);
  }

  private JsonNode executeForJson(HttpRequest request, String opName) {
    try {
      HttpResponse<String> response = send(request);
      if (response.statusCode() / 100 != 2) {
        log.warn("{} failed: status={} body={}", opName, response.statusCode(), response.body());
        return null;
      }
      return objectMapper.readTree(response.body());
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      log.warn("{} IO error", opName, e);
      return null;
    }
  }

  public static String limit(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
  }
}
