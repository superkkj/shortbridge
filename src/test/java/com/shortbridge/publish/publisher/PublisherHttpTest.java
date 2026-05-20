package com.shortbridge.publish.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PublisherHttp")
class PublisherHttpTest {

  private HttpServer server;
  private String baseUrl;
  private PublisherHttp publisherHttp;
  private final List<RecordedRequest> recorded = new ArrayList<>();

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    server.start();
    publisherHttp = new PublisherHttp(HttpClient.newHttpClient(), new ObjectMapper());
  }

  @AfterEach
  void stopServer() {
    if (server != null) server.stop(0);
    recorded.clear();
  }

  private void register(String path, int status, String responseBody) {
    server.createContext(path, exchange -> handle(exchange, status, responseBody));
  }

  private void handle(HttpExchange exchange, int status, String responseBody) throws IOException {
    try (InputStream is = exchange.getRequestBody()) {
      byte[] body = is.readAllBytes();
      recorded.add(
          new RecordedRequest(
              exchange.getRequestMethod(),
              exchange.getRequestURI().getPath(),
              exchange.getRequestHeaders().getFirst("Authorization"),
              exchange.getRequestHeaders().getFirst("Content-Type"),
              new String(body, StandardCharsets.UTF_8)));
    }
    byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, response.length);
    exchange.getResponseBody().write(response);
    exchange.close();
  }

  @Nested
  @DisplayName("bearer / jsonBearer 빌더")
  class BuilderTests {

    @Test
    @DisplayName("토큰이 있으면 Authorization: Bearer 가 실제 요청에 실린다")
    void bearer_withToken_sendsAuthorizationHeader() throws Exception {
      register("/echo", 200, "{}");

      HttpRequest request =
          publisherHttp.bearer(baseUrl + "/echo", "abc").GET().build();
      HttpResponse<String> response = publisherHttp.send(request);

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(recorded).hasSize(1);
      assertThat(recorded.get(0).authorization()).isEqualTo("Bearer abc");
    }

    @Test
    @DisplayName("토큰이 null 이면 Authorization 헤더 없음")
    void bearer_nullToken_omitsAuthorizationHeader() throws Exception {
      register("/echo", 200, "{}");

      HttpRequest request = publisherHttp.bearer(baseUrl + "/echo", null).GET().build();
      publisherHttp.send(request);

      assertThat(recorded.get(0).authorization()).isNull();
    }

    @Test
    @DisplayName("토큰이 공백이면 Authorization 헤더 없음")
    void bearer_blankToken_omitsAuthorizationHeader() throws Exception {
      register("/echo", 200, "{}");

      HttpRequest request = publisherHttp.bearer(baseUrl + "/echo", "  ").GET().build();
      publisherHttp.send(request);

      assertThat(recorded.get(0).authorization()).isNull();
    }

    @Test
    @DisplayName("jsonBearer 는 Content-Type: application/json; charset=UTF-8 을 추가한다")
    void jsonBearer_addsJsonContentType() throws Exception {
      register("/echo", 200, "{}");

      HttpRequest request =
          publisherHttp
              .jsonBearer(baseUrl + "/echo", "abc")
              .POST(HttpRequest.BodyPublishers.ofString("{}"))
              .build();
      publisherHttp.send(request);

      assertThat(recorded.get(0).contentType()).isEqualTo("application/json; charset=UTF-8");
      assertThat(recorded.get(0).authorization()).isEqualTo("Bearer abc");
    }
  }

  @Nested
  @DisplayName("postJson")
  class PostJsonTests {

    @Test
    @DisplayName("2xx 응답이면 JsonNode 를 반환한다")
    void postJson_2xx_returnsJsonNode() {
      register("/x", 200, "{\"id\":\"X\",\"ok\":true}");

      JsonNode node = publisherHttp.postJson(baseUrl + "/x", "tok", new Payload("hello"), "op");

      assertThat(node).isNotNull();
      assertThat(node.get("id").asText()).isEqualTo("X");
      assertThat(node.get("ok").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("4xx 응답이면 null 을 반환한다")
    void postJson_4xx_returnsNull() {
      register("/x", 400, "{\"error\":\"bad\"}");

      JsonNode node = publisherHttp.postJson(baseUrl + "/x", "tok", new Payload("h"), "op");

      assertThat(node).isNull();
    }

    @Test
    @DisplayName("connection 실패 (서버 미기동) → null 반환")
    void postJson_connectionRefused_returnsNull() throws IOException {
      int unusedPort = unusedPort();

      JsonNode node = publisherHttp.postJson(
          "http://127.0.0.1:" + unusedPort + "/x", "tok", new Payload("h"), "op");

      assertThat(node).isNull();
    }

    @Test
    @DisplayName("직렬화 불가능한 객체(순환 참조) → null 반환")
    void postJson_unserializableBody_returnsNull() {
      register("/x", 200, "{}");

      JsonNode node = publisherHttp.postJson(baseUrl + "/x", "tok", new SelfReferencing(), "op");

      assertThat(node).isNull();
      assertThat(recorded).isEmpty(); // 직렬화 단계에서 실패하므로 요청 자체가 발생하지 않아야 함
    }

    @Test
    @DisplayName("요청은 POST + JSON content-type + 직렬화된 body 가 실제로 전송된다")
    void postJson_sendsPostWithJsonBody() {
      register("/x", 204, "{}");

      publisherHttp.postJson(baseUrl + "/x", "tok", new Payload("hello"), "op");

      assertThat(recorded).hasSize(1);
      RecordedRequest req = recorded.get(0);
      assertThat(req.method()).isEqualTo("POST");
      assertThat(req.path()).isEqualTo("/x");
      assertThat(req.contentType()).isEqualTo("application/json; charset=UTF-8");
      assertThat(req.body()).isEqualTo("{\"value\":\"hello\"}");
    }
  }

  @Nested
  @DisplayName("postEmpty")
  class PostEmptyTests {

    @Test
    @DisplayName("body 가 없는 POST 를 보내고 응답을 JsonNode 로 반환한다")
    void postEmpty_sendsNoBodyPost() {
      register("/refresh", 200, "{\"refreshed\":true}");

      JsonNode node = publisherHttp.postEmpty(baseUrl + "/refresh", "tok", "refresh");

      assertThat(node).isNotNull();
      assertThat(node.get("refreshed").asBoolean()).isTrue();
      assertThat(recorded).hasSize(1);
      assertThat(recorded.get(0).method()).isEqualTo("POST");
      assertThat(recorded.get(0).body()).isEmpty();
    }
  }

  @Nested
  @DisplayName("parse / writeJson")
  class SerializationTests {

    @Test
    @DisplayName("parse 는 JSON 문자열을 JsonNode 로 변환한다")
    void parse_validJson_returnsJsonNode() throws Exception {
      JsonNode node = publisherHttp.parse("{\"a\":1,\"b\":\"x\"}");

      assertThat(node.get("a").asInt()).isEqualTo(1);
      assertThat(node.get("b").asText()).isEqualTo("x");
    }

    @Test
    @DisplayName("parse 는 잘못된 JSON 에 대해 IOException 을 던진다")
    void parse_invalidJson_throwsIOException() {
      assertThatThrownBy(() -> publisherHttp.parse("not json"))
          .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("writeJson 은 객체를 JSON 문자열로 직렬화한다")
    void writeJson_serializesObject() throws Exception {
      String json = publisherHttp.writeJson(new Payload("hi"));

      assertThat(json).contains("\"value\":\"hi\"");
    }
  }

  @Nested
  @DisplayName("uploadBuilder")
  class UploadBuilderTests {

    @Test
    @DisplayName("Content-Type 헤더와 5분 타임아웃이 실제 요청에 적용된다")
    void uploadBuilder_setsContentTypeAndTimeout() throws Exception {
      register("/upload", 200, "{}");
      AtomicReference<Duration> capturedTimeout = new AtomicReference<>();

      HttpRequest request =
          publisherHttp
              .uploadBuilder(baseUrl + "/upload", "application/octet-stream")
              .PUT(HttpRequest.BodyPublishers.ofString("payload"))
              .build();
      capturedTimeout.set(request.timeout().orElse(null));
      publisherHttp.send(request);

      assertThat(capturedTimeout.get()).isEqualTo(Duration.ofMinutes(5));
      assertThat(recorded).hasSize(1);
      assertThat(recorded.get(0).contentType()).isEqualTo("application/octet-stream");
      assertThat(recorded.get(0).method()).isEqualTo("PUT");
    }
  }

  @Nested
  @DisplayName("limit (static)")
  class LimitTests {

    @Test
    void limit_null_returnsNull() {
      assertThat(PublisherHttp.limit(null, 10)).isNull();
    }

    @Test
    void limit_shorterThanMax_returnsAsIs() {
      assertThat(PublisherHttp.limit("abc", 10)).isEqualTo("abc");
    }

    @Test
    void limit_exactlyMax_returnsAsIs() {
      assertThat(PublisherHttp.limit("abcde", 5)).isEqualTo("abcde");
    }

    @Test
    void limit_longerThanMax_truncates() {
      assertThat(PublisherHttp.limit("abcdef", 3)).isEqualTo("abc");
    }
  }

  private static int unusedPort() throws IOException {
    try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
      return ss.getLocalPort();
    }
  }

  private record RecordedRequest(
      String method, String path, String authorization, String contentType, String body) {}

  private record Payload(String value) {}

  /** Jackson 이 직렬화하지 못하는 순환 참조 객체. */
  private static class SelfReferencing {
    @SuppressWarnings("unused")
    public SelfReferencing getSelf() {
      return this;
    }
  }
}
