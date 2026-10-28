package dev.tchiwara.movieradar.movies;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Remembers which (client, movie) pairs were counted recently, so typing "masterp",
 * "masterpla", "masterplan" counts the movie once. In memory and per instance.
 */
@Component
public class CountDeduplicator {
    private final Cache<String, Boolean> recent;

    @Autowired
    public CountDeduplicator(@Value("${app.count-dedupe-minutes:10}") long windowMinutes) {
        this(Duration.ofMinutes(requirePositive(windowMinutes)), Ticker.systemTicker());
    }

    CountDeduplicator(Duration window, Ticker ticker) {
        this.recent = Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(10_000)
                .ticker(ticker)
                .build();
    }

    /** True the first time this client counts this movie within the window. */
    public boolean firstInWindow(String clientIp, int movieId) {
        return recent.asMap().putIfAbsent(key(clientIp, movieId), Boolean.TRUE) == null;
    }

    /** Lets the next search count again, used when counting failed. */
    public void forget(String clientIp, int movieId) {
        recent.invalidate(key(clientIp, movieId));
    }

    private static String key(String clientIp, int movieId) {
        return clientIp + ":" + movieId;
    }

    private static long requirePositive(long minutes) {
        if (minutes <= 0) {
            throw new IllegalStateException("COUNT_DEDUPE_MINUTES must be greater than zero");
        }
        return minutes;
    }
}