package dev.tchiwara.movieradar.movies;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingSafetyTest {
    @Test
    void tmdbSecretIsRedactedFromFailureMessage() {
        String secret = "tmdb-secret-value";
        String safe = MovieClient.safeMessage(new RuntimeException("Bearer " + secret), secret);
        assertFalse(safe.contains(secret));
        assertTrue(safe.contains("[REDACTED]"));
    }
}
