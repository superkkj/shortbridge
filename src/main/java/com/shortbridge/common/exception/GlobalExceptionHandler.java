package com.shortbridge.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

  private static final String ERROR_VIEW = "error/error";

  @ExceptionHandler(ShortBridgeException.class)
  public Object handleShortBridge(ShortBridgeException ex, HttpServletRequest request) {
    log.warn("ShortBridgeException: code={} message={}", ex.getErrorCode().getCode(), ex.getMessage());
    ErrorCode errorCode = ex.getErrorCode();
    return render(request, errorCode, ex.getMessage(), null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public Object handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<ErrorResponse.FieldError> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
            .toList();
    return render(request, ErrorCode.VALIDATION_FAILED,
        ErrorCode.VALIDATION_FAILED.getMessageTemplate(), fieldErrors);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public Object handleUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
    return render(request, ErrorCode.VIDEO_SIZE_EXCEEDED,
        ErrorCode.VIDEO_SIZE_EXCEEDED.format(ex.getMessage()), null);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
    return render(request, ErrorCode.FORBIDDEN,
        ErrorCode.FORBIDDEN.getMessageTemplate(), null);
  }

  @ExceptionHandler({
      NoHandlerFoundException.class,
      NoResourceFoundException.class,
      ResponseStatusException.class
  })
  public Object handleNotFound(Exception ex, HttpServletRequest request) {
    int status = (ex instanceof ResponseStatusException rse) ? rse.getStatusCode().value() : 404;
    ErrorCode code = (status == 405) ? ErrorCode.METHOD_NOT_ALLOWED : ErrorCode.ROUTE_NOT_FOUND;
    log.info("Route not found: status={} path={}", status, request.getRequestURI());
    return render(request, code, code.getMessageTemplate(), null);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public Object handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    log.info("Method not allowed: method={} path={}", ex.getMethod(), request.getRequestURI());
    return render(request, ErrorCode.METHOD_NOT_ALLOWED,
        ErrorCode.METHOD_NOT_ALLOWED.getMessageTemplate(), null);
  }

  @ExceptionHandler(Exception.class)
  public Object handleAny(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception: path={}", request.getRequestURI(), ex);
    return render(request, ErrorCode.INTERNAL_ERROR,
        ErrorCode.INTERNAL_ERROR.getMessageTemplate(), null);
  }

  private Object render(HttpServletRequest request, ErrorCode errorCode,
                        String message, List<ErrorResponse.FieldError> fieldErrors) {
    int status = errorCode.getStatus();
    if (wantsJson(request)) {
      ErrorResponse body = fieldErrors == null
          ? ErrorResponse.of(errorCode, message, request.getRequestURI())
          : ErrorResponse.of(errorCode, message, request.getRequestURI(), fieldErrors);
      return ResponseEntity.status(status).body(body);
    }
    ModelAndView mav = new ModelAndView(ERROR_VIEW);
    mav.setStatus(HttpStatus.valueOf(status));
    mav.addObject("status", status);
    mav.addObject("error", errorCode.getCode());
    mav.addObject("message", message);
    return mav;
  }

  private boolean wantsJson(HttpServletRequest request) {
    String uri = request.getRequestURI();
    if (uri != null && uri.startsWith("/api/")) {
      return true;
    }
    String accept = request.getHeader("Accept");
    return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);
  }
}
