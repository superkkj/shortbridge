package com.shortbridge.common.logging;

import java.util.regex.Pattern;

/**
 * 외부 API 응답 body 를 로그로 남기기 전 sensitive 필드를 마스킹하고 길이를 제한한다.
 *
 * <p>OAuth token 교환의 4xx/5xx 응답은 정상 토큰을 포함하지 않지만, provider 가
 * 헬프 메시지에 access_token / refresh_token / id_token / client_secret 같은 식별자를
 * 끼워 넣는 경우가 있어 일괄 마스킹한다. 5xx 폭주 시 로그 폭증도 길이 제한으로 방어.
 */
public final class SensitiveLog {

  private static final int MAX_BODY_CHARS = 500;

  private static final Pattern TOKEN_PATTERN =
      Pattern.compile(
          "\"(access_token|refresh_token|id_token|client_secret|code)\"\\s*:\\s*\"([^\"]*)\"",
          Pattern.CASE_INSENSITIVE);

  private SensitiveLog() {}

  public static String maskTokens(String body) {
    if (body == null) {
      return null;
    }
    String masked = TOKEN_PATTERN.matcher(body).replaceAll("\"$1\":\"***MASKED***\"");
    return truncate(masked);
  }

  public static String truncate(String body) {
    if (body == null) {
      return null;
    }
    if (body.length() <= MAX_BODY_CHARS) {
      return body;
    }
    return body.substring(0, MAX_BODY_CHARS) + "...(truncated " + (body.length() - MAX_BODY_CHARS) + " chars)";
  }
}
