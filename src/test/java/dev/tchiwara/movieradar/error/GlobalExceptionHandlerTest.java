package dev.tchiwara.movieradar.error;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GlobalExceptionHandlerTest {
    @Test void invalidInputIsBadRequest() {
        var result = new GlobalExceptionHandler().invalid(new IllegalArgumentException("bad"));
        assertEquals(400, result.getBody().status()); assertEquals("INVALID_INPUT", result.getBody().code());
    }

    @Test void upstreamTimeoutIsGatewayTimeout() {
        var result = new GlobalExceptionHandler().upstream(
                new UpstreamException("TMDB timed out", true));
        assertEquals(504, result.getBody().status());
        assertEquals("UPSTREAM_TIMEOUT", result.getBody().code());
    }
}
