package com.shortbridge.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SensitiveLog (token masking + length cap)")
class SensitiveLogTest {

  @Test
  void maskTokens_replacesAccessToken() {
    String body = "{\"access_token\":\"ya29.abcdef\",\"expires_in\":3600}";

    String masked = SensitiveLog.maskTokens(body);

    assertThat(masked).contains("\"access_token\":\"***MASKED***\"");
    assertThat(masked).doesNotContain("ya29.abcdef");
    assertThat(masked).contains("expires_in");
  }

  @Test
  void maskTokens_replacesAllSensitiveKeys() {
    String body =
        "{\"access_token\":\"a\",\"refresh_token\":\"r\",\"id_token\":\"i\",\"client_secret\":\"s\",\"code\":\"c\"}";

    String masked = SensitiveLog.maskTokens(body);

    assertThat(masked).doesNotContain("\"a\"");
    assertThat(masked).doesNotContain("\"r\"");
    assertThat(masked).doesNotContain("\"i\"");
    assertThat(masked).doesNotContain("\"s\"");
    assertThat(masked).doesNotContain("\"c\"");
    assertThat(masked).contains("***MASKED***");
  }

  @Test
  void maskTokens_caseInsensitive() {
    String body = "{\"Access_Token\":\"X\"}";

    String masked = SensitiveLog.maskTokens(body);

    assertThat(masked).contains("***MASKED***");
    assertThat(masked).doesNotContain("\"X\"");
  }

  @Test
  void maskTokens_null_returnsNull() {
    assertThat(SensitiveLog.maskTokens(null)).isNull();
  }

  @Test
  void maskTokens_noSensitiveKeys_returnsAsIs() {
    String body = "{\"error\":\"invalid_grant\",\"error_description\":\"bad code\"}";

    assertThat(SensitiveLog.maskTokens(body)).isEqualTo(body);
  }

  @Test
  void truncate_overLimit_truncatesWithMarker() {
    String body = "x".repeat(600);

    String truncated = SensitiveLog.truncate(body);

    assertThat(truncated).hasSize(500 + "...(truncated 100 chars)".length());
    assertThat(truncated).endsWith("...(truncated 100 chars)");
  }

  @Test
  void truncate_underLimit_returnsAsIs() {
    String body = "short body";

    assertThat(SensitiveLog.truncate(body)).isEqualTo(body);
  }

  @Test
  void maskTokens_alsoEnforcesLengthCap() {
    String big = "{\"access_token\":\"" + "a".repeat(1000) + "\"}";

    String result = SensitiveLog.maskTokens(big);

    assertThat(result).contains("***MASKED***");
    assertThat(result.length()).isLessThanOrEqualTo(500 + 40);
  }
}
