package dev.tchiwara.movieradar.movies;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovieClientTest {
    @Test
    void mapsTmdbTotalPages() throws Exception {
        MovieClient.TmdbResponse response = new ObjectMapper().readValue(
                "{\"page\":2,\"total_pages\":37,\"results\":[]}", MovieClient.TmdbResponse.class);
        assertEquals(37, response.totalPages);
    }

    @Test
    void recognizesJdkHttpTimeouts() {
        assertTrue(MovieClient.isTimeout(new java.net.http.HttpTimeoutException("read timed out")));
        assertTrue(MovieClient.isTimeout(new java.net.http.HttpConnectTimeoutException("connect timed out")));
    }
}
