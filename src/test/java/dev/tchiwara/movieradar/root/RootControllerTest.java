package dev.tchiwara.movieradar.root;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RootControllerTest {
    @Test void rootReturnsWelcomeMessage() {
        var body = new RootController().home();
        assertEquals("Movie Radar BFF", body.get("application"));
        assertEquals("running", body.get("status"));
        assertEquals("/api/movies/popular", body.get("movies"));
    }
}