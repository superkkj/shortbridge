package com.shortbridge.publish.tiktok;

import com.fasterxml.jackson.databind.JsonNode;
import com.shortbridge.common.storage.StorageClient;
import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.platform.socialaccount.domain.SocialAccount;
import com.shortbridge.platform.video.domain.Video;
import com.shortbridge.publish.dto.PublishOutcome;
import com.shortbridge.publish.publisher.PublishContext;
import com.shortbridge.publish.publisher.PublisherHttp;
import com.shortbridge.publish.publisher.SocialPublisher;
import com.shortbridge.support.security.token.PlatformTokenCipher;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TikTokPublisher implements SocialPublisher {

  private static final String CREATOR_INFO_URL =
      "https://open.tiktokapis.com/v2/post/publish/creator_info/query/";
  private static final String INIT_URL = "https://open.tiktokapis.com/v2/post/publish/video/init/";
  private static final String STATUS_URL = "https://open.tiktokapis.com/v2/post/publish/status/fetch/";
  private static final long CHUNK_SIZE = 10L * 1024 * 1024;

  private final PlatformTokenCipher tokenCipher;
  private final StorageClient storageClient;
  private final PublisherHttp http;

  @Override
  public Platform platform() {
    return Platform.TIKTOK;
  }

  @Override
  public PublishOutcome publish(PublishContext context) {
    SocialAccount account = context.socialAccount();
    Video video = context.video();

    String accessToken;
    try {
      accessToken = tokenCipher.decrypt(account.getAccessTokenEncrypted());
    } catch (RuntimeException e) {
      return PublishOutcome.reconnectRequired("token decrypt failed: " + e.getMessage());
    }
    if (accessToken == null || accessToken.isBlank()) {
      return PublishOutcome.reconnectRequired("access token empty");
    }

    JsonNode creatorInfo = http.postEmpty(CREATOR_INFO_URL, accessToken, "tiktok creator_info");
    if (creatorInfo == null) {
      return PublishOutcome.failedTemporary(
          "TT_CREATOR_INFO_FAIL", "creator_info query failed", Instant.now().plusSeconds(60));
    }

    long fileSize = video.getFileSize();
    long chunkSize = Math.min(CHUNK_SIZE, fileSize);
    int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);

    JsonNode initResult =
        http.postJson(INIT_URL, accessToken, buildInitBody(context, video, fileSize, chunkSize, totalChunks), "tiktok video/init");
    if (initResult == null) {
      return PublishOutcome.failedTemporary(
          "TT_INIT_FAIL", "video/init failed", Instant.now().plusSeconds(60));
    }
    String publishId = initResult.path("data").path("publish_id").asText(null);
    String uploadUrl = initResult.path("data").path("upload_url").asText(null);
    if (publishId == null || uploadUrl == null) {
      return PublishOutcome.failedPermanent("TT_INIT_NO_URL", initResult.toString());
    }

    PublishOutcome uploadOutcome = uploadChunks(uploadUrl, video, fileSize, chunkSize, totalChunks);
    if (uploadOutcome != null) return uploadOutcome;

    JsonNode statusJson = pollStatus(accessToken, publishId);
    String status = statusJson == null ? null : statusJson.path("data").path("status").asText(null);
    String externalPostId = statusJson == null ? null : statusJson.path("data").path("publicaly_available_post_id").asText(null);

    log.info("tiktok publish completed: publishId={} status={} externalPostId={}", publishId, status, externalPostId);
    return PublishOutcome.success(externalPostId == null ? publishId : externalPostId, publishId, statusJson == null ? null : statusJson.toString());
  }

  private Map<String, Object> buildInitBody(
      PublishContext context, Video video, long fileSize, long chunkSize, int totalChunks) {
    Map<String, Object> postInfo = new HashMap<>();
    String title = context.target().getPlatformTitle();
    if (title == null || title.isBlank()) title = video.getOriginalFileName();
    postInfo.put("title", PublisherHttp.limit(title, 150));
    postInfo.put("privacy_level", "SELF_ONLY");
    postInfo.put("disable_duet", false);
    postInfo.put("disable_comment", false);
    postInfo.put("disable_stitch", false);

    Map<String, Object> sourceInfo = new HashMap<>();
    sourceInfo.put("source", "FILE_UPLOAD");
    sourceInfo.put("video_size", fileSize);
    sourceInfo.put("chunk_size", chunkSize);
    sourceInfo.put("total_chunk_count", totalChunks);

    Map<String, Object> body = new HashMap<>();
    body.put("post_info", postInfo);
    body.put("source_info", sourceInfo);
    return body;
  }

  private PublishOutcome uploadChunks(
      String uploadUrl, Video video, long fileSize, long chunkSize, int totalChunks) {
    try (InputStream is = storageClient.open(video.getStorageKey())) {
      for (int i = 0; i < totalChunks; i++) {
        long start = i * chunkSize;
        long end = Math.min(start + chunkSize, fileSize) - 1;
        int len = (int) (end - start + 1);
        byte[] chunk = readExactly(is, len);

        HttpRequest request =
            http.uploadBuilder(uploadUrl, "video/mp4")
                .header("Content-Range", "bytes " + start + "-" + end + "/" + fileSize)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(chunk))
                .build();
        HttpResponse<String> response = http.send(request);
        if (response.statusCode() != 201 && response.statusCode() != 206) {
          log.warn(
              "tiktok chunk upload failed: chunk={} status={} body={}",
              i,
              response.statusCode(),
              response.body());
          return PublishOutcome.failedTemporary(
              "TT_CHUNK_" + response.statusCode(), response.body(), Instant.now().plusSeconds(60));
        }
      }
      return null;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      log.error("tiktok chunk upload IO error", e);
      return PublishOutcome.failedTemporary(
          "TT_UPLOAD_IO", e.getMessage(), Instant.now().plusSeconds(60));
    }
  }

  private static byte[] readExactly(InputStream is, int len) throws IOException {
    byte[] buf = new byte[len];
    int off = 0;
    while (off < len) {
      int n = is.read(buf, off, len - off);
      if (n < 0) {
        throw new IOException("unexpected EOF: read " + off + " of " + len);
      }
      off += n;
    }
    return buf;
  }

  private JsonNode pollStatus(String accessToken, String publishId) {
    Map<String, Object> body = Map.of("publish_id", publishId);
    for (int i = 0; i < 12; i++) {
      JsonNode root = http.postJson(STATUS_URL, accessToken, body, "tiktok status/fetch");
      if (root == null) return null;
      String status = root.path("data").path("status").asText("");
      if ("PUBLISH_COMPLETE".equals(status) || "FAILED".equals(status)) {
        return root;
      }
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
    return null;
  }
}
