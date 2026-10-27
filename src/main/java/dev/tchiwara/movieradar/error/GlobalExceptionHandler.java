package dev.tchiwara.movieradar.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid(IllegalArgumentException e) {
        return mapped(400, "INVALID_INPUT", e.getMessage(), e, false);
    }
    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ApiError> limited(RateLimitExceededException e, HttpServletRequest request) {
        return ResponseEntity.status(429).header("Retry-After", "60")
                .body(new ApiError(429, "RATE_LIMITED", "Too many requests"));
    }
    @ExceptionHandler(UpstreamException.class)
    ResponseEntity<ApiError> upstream(UpstreamException e) {
        boolean misconfigured = e.status() == 401 || e.status() == 403;
        int status = e.isTimeout() ? 504 : 503;
        String code = misconfigured ? "UPSTREAM_MISCONFIGURED"
                : e.isTimeout() ? "UPSTREAM_TIMEOUT" : "UPSTREAM_UNAVAILABLE";
        return mapped(status, code, misconfigured ? "Upstream credentials are misconfigured" : e.getMessage(),
                e, misconfigured);
    }
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ApiError> notFound(Exception e) {
        return quiet(404, "NOT_FOUND", "Resource not found");
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return quiet(405, "METHOD_NOT_ALLOWED", "Method not allowed");
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> other(Exception e) {
        return mapped(500, "INTERNAL_ERROR", "An unexpected error occurred", e, false);
    }
    private ResponseEntity<ApiError> mapped(int status, String code, String message, Exception exception, boolean error) {
        String requestId = MDC.get(dev.tchiwara.movieradar.config.RequestLoggingFilter.REQUEST_ID);
        if (error) {
            log.error("mapped error requestId={} status={} code={} hint=check TMDB_TOKEN",
                    requestId, status, code);
        } else {
            log.warn("mapped error requestId={} status={} code={}", requestId, status, code);
        }
        log.debug("mapped error stack requestId={} status={} code={}", requestId, status, code, exception);
        return ResponseEntity.status(status).body(new ApiError(status, code, message));
    }
    private ResponseEntity<ApiError> quiet(int status, String code, String message) {
        String requestId = MDC.get(dev.tchiwara.movieradar.config.RequestLoggingFilter.REQUEST_ID);
        log.info("mapped error requestId={} status={} code={}", requestId, status, code);
        return ResponseEntity.status(status).body(new ApiError(status, code, message));
    }
    public record ApiError(int status, String code, String message) {}
}