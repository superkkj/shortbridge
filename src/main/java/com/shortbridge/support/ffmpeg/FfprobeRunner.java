package com.shortbridge.support.ffmpeg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(FfprobeProperties.class)
public class FfprobeRunner {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final FfprobeProperties properties;

  public VideoMetadata probe(Path file) {
    try {
      ProcessBuilder pb =
          new ProcessBuilder(
              properties.resolvedPath(),
              "-v",
              "error",
              "-print_format",
              "json",
              "-show_streams",
              "-show_format",
              file.toAbsolutePath().toString());
      pb.redirectErrorStream(true);
      Process process = pb.start();
      String output = new String(process.getInputStream().readAllBytes());
      boolean finished = process.waitFor(30, TimeUnit.SECONDS);
      if (!finished || process.exitValue() != 0) {
        log.warn("ffprobe failed: exit={} output={}", process.exitValue(), output);
        throw new ShortBridgeException(ErrorCode.VIDEO_PROBE_FAILED);
      }
      return parse(output);
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw ShortBridgeException.of(ErrorCode.VIDEO_PROBE_FAILED, e);
    }
  }

  private VideoMetadata parse(String json) throws IOException {
    JsonNode root = MAPPER.readTree(json);
    JsonNode format = root.path("format");
    double duration = format.path("duration").asDouble(0);
    int durationSeconds = (int) Math.round(duration);

    Integer width = null;
    Integer height = null;
    String videoCodec = null;
    String audioCodec = null;

    for (JsonNode stream : root.path("streams")) {
      String codecType = stream.path("codec_type").asText("");
      if ("video".equals(codecType) && videoCodec == null) {
        videoCodec = stream.path("codec_name").asText(null);
        width = stream.has("width") ? stream.get("width").asInt() : null;
        height = stream.has("height") ? stream.get("height").asInt() : null;
      } else if ("audio".equals(codecType) && audioCodec == null) {
        audioCodec = stream.path("codec_name").asText(null);
      }
    }

    return new VideoMetadata(durationSeconds, width, height, videoCodec, audioCodec);
  }
}
