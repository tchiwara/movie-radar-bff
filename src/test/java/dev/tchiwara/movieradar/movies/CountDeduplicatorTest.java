package dev.tchiwara.movieradar.movies;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountDeduplicatorTest {
    private final AtomicLong nanos = new AtomicLong();
    private final CountDeduplicator dedupe = new CountDeduplicator(Duration.ofMinutes(10), nanos::get);

    @Test void sameClientAndMovieIsOnlyFirstInWindow() {
        assertTrue(dedupe.firstInWindow("1.1.1.1", 5));
        assertFalse(dedupe.firstInWindow("1.1.1.1", 5));
    }

    @Test void differentClientOrMovieIsCountedSeparately() {
        assertTrue(dedupe.firstInWindow("1.1.1.1", 5));
        assertTrue(dedupe.firstInWindow("2.2.2.2", 5));
        assertTrue(dedupe.firstInWindow("1.1.1.1", 6));
    }

    @Test void entryExpiresAfterTheWindow() {
        assertTrue(dedupe.firstInWindow("1.1.1.1", 5));
        nanos.addAndGet(Duration.ofMinutes(11).toNanos());
        assertTrue(dedupe.firstInWindow("1.1.1.1", 5));
    }

    @Test void forgetAllowsCountingAgain() {
        assertTrue(dedupe.firstInWindow("1.1.1.1", 5));
        dedupe.forget("1.1.1.1", 5);
        assertTrue(dedupe.firstInWindow("1.1.1.1", 5));
    }
}