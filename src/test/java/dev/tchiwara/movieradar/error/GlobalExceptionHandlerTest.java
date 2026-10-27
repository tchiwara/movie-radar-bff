package dev.tchiwara.movieradar.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
    @Test void invalidInputIsBadRequest() {
        var result = new GlobalExceptionHandler().invalid(new IllegalArgumentException("bad"));
        assertEquals(400, result.getBody().status());
        assertEquals("INVALID_INPUT", result.getBody().code());
    }

    @Test void upstreamTimeoutIsGatewayTimeout() {
        var result = new GlobalExceptionHandler().upstream(
                new UpstreamException("TMDB timed out", true));
        assertEquals(504, result.getBody().status());
        assertEquals("UPSTREAM_TIMEOUT", result.getBody().code());
    }

    @Test void unknownRouteIsNotFound() {
        var result = new GlobalExceptionHandler().notFound(
                new NoHandlerFoundException("GET", "/nope", HttpHeaders.EMPTY));
        assertEquals(404, result.getBody().status());
        assertEquals("NOT_FOUND", result.getBody().code());
        assertEquals("Resource not found", result.getBody().message());
    }

    @Test void missingResourceIsNotFound() {
        var result = new GlobalExceptionHandler().notFound(
                new NoResourceFoundException(HttpMethod.GET, "/", "/"));
        assertEquals(404, result.getBody().status());
        assertEquals("NOT_FOUND", result.getBody().code());
    }

    @Test void wrongMethodIsMethodNotAllowed() {
        var result = new GlobalExceptionHandler().methodNotAllowed(
                new HttpRequestMethodNotSupportedException("POST"));
        assertEquals(405, result.getBody().status());
        assertEquals("METHOD_NOT_ALLOWED", result.getBody().code());
        assertEquals("Method not allowed", result.getBody().message());
    }

    @Test void unexpectedErrorStaysInternalError() {
        var result = new GlobalExceptionHandler().other(new RuntimeException("boom"));
        assertEquals(500, result.getBody().status());
        assertEquals("INTERNAL_ERROR", result.getBody().code());
        assertEquals("An unexpected error occurred", result.getBody().message());
    }
}