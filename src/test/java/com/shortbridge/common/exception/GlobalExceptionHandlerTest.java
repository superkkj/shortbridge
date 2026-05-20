package com.shortbridge.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
    viewResolver.setPrefix("/WEB-INF/views/");
    viewResolver.setSuffix(".html");

    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .setViewResolvers(viewResolver)
            .build();
  }

  @Test
  @DisplayName("/api/** + ShortBridgeException → JSON 응답 + ErrorCode status/code 매핑")
  void apiShortBridge_returnsJsonError() throws Exception {
    mockMvc
        .perform(get("/api/throw/shortbridge"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(ErrorCode.POST_NOT_FOUND.getCode()))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.path").value("/api/throw/shortbridge"))
        .andExpect(jsonPath("$.errors").isArray());
  }

  @Test
  @DisplayName("HTML 라우트 + ShortBridgeException → error/error view + 모델 속성")
  void htmlShortBridge_returnsErrorView() throws Exception {
    mockMvc
        .perform(get("/html/throw/shortbridge"))
        .andExpect(status().isNotFound())
        .andExpect(view().name("error/error"))
        .andExpect(model().attribute("status", 404))
        .andExpect(model().attribute("error", ErrorCode.POST_NOT_FOUND.getCode()))
        .andExpect(model().attributeExists("message"));
  }

  @Test
  @DisplayName("/api/** + 검증 실패 → JSON + errors 배열에 필드 에러 포함")
  void apiValidation_returnsJsonWithFieldErrors() throws Exception {
    mockMvc
        .perform(
            post("/api/throw/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  @DisplayName("HTML 라우트 + 검증 실패 → error/error view")
  void htmlValidation_returnsErrorView() throws Exception {
    mockMvc
        .perform(
            post("/html/throw/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(view().name("error/error"))
        .andExpect(model().attribute("error", ErrorCode.VALIDATION_FAILED.getCode()));
  }

  @Test
  @DisplayName("/api/** + AccessDeniedException → 403 + FORBIDDEN code")
  void apiAccessDenied_returns403Json() throws Exception {
    mockMvc
        .perform(get("/api/throw/access-denied"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  @DisplayName("/api/** + 잡히지 않은 Exception → 500 + INTERNAL_ERROR")
  void apiGenericException_returns500Json() throws Exception {
    mockMvc
        .perform(get("/api/throw/generic"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR.getCode()))
        .andExpect(jsonPath("$.status").value(500));
  }

  @Test
  @DisplayName("/api/** + 허용되지 않은 HTTP 메서드 → 405 + METHOD_NOT_ALLOWED")
  void apiMethodNotAllowed_returns405Json() throws Exception {
    mockMvc
        .perform(post("/api/get-only"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value(ErrorCode.METHOD_NOT_ALLOWED.getCode()))
        .andExpect(jsonPath("$.status").value(405));
  }

  @Test
  @DisplayName("HTML 라우트지만 Accept: application/json 헤더가 있으면 JSON 으로 응답")
  void htmlRouteWithJsonAcceptHeader_returnsJson() throws Exception {
    mockMvc
        .perform(get("/html/throw/shortbridge").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(ErrorCode.POST_NOT_FOUND.getCode()));
  }

  @RestController
  static class TestController {

    @GetMapping("/api/throw/shortbridge")
    void apiThrowShortBridge() {
      throw ShortBridgeException.of(ErrorCode.POST_NOT_FOUND, UUID.randomUUID());
    }

    @GetMapping("/html/throw/shortbridge")
    void htmlThrowShortBridge() {
      throw ShortBridgeException.of(ErrorCode.POST_NOT_FOUND, UUID.randomUUID());
    }

    @PostMapping("/api/throw/validation")
    void apiValidation(@RequestBody @jakarta.validation.Valid SampleBody body) {
      // never reached when body invalid
    }

    @PostMapping("/html/throw/validation")
    void htmlValidation(@RequestBody @jakarta.validation.Valid SampleBody body) {
      // never reached when body invalid
    }

    @GetMapping("/api/throw/access-denied")
    void apiThrowAccessDenied() {
      throw new AccessDeniedException("denied");
    }

    @GetMapping("/api/throw/generic")
    void apiThrowGeneric() {
      throw new IllegalStateException("boom");
    }

    @GetMapping("/api/get-only")
    void apiGetOnly() {
      // exists only for GET → POST returns 405
    }
  }

  record SampleBody(@NotBlank String name) {}
}
