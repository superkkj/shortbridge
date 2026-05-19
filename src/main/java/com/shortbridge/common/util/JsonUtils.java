package com.shortbridge.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JsonUtils {

  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

  private JsonUtils() {}

  public static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new ShortBridgeException(ErrorCode.JSON_PROCESSING_ERROR);
    }
  }

  public static String joinHashtags(List<String> hashtags) {
    if (hashtags == null || hashtags.isEmpty()) return "";
    return hashtags.stream().map(JsonUtils::stripHash).collect(Collectors.joining(","));
  }

  public static List<String> splitHashtags(String csv) {
    if (csv == null || csv.isBlank()) return List.of();
    return Stream.of(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  private static String stripHash(String tag) {
    if (tag == null) return "";
    String trimmed = tag.trim();
    return trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
  }
}
