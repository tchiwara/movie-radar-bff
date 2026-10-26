package dev.tchiwara.movieradar.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import dev.tchiwara.movieradar.config.AppProperties;
import dev.tchiwara.movieradar.config.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private final AppProperties properties;
    private final ConcurrentHashMap<String, Entry> buckets = new ConcurrentHashMap<>();
    public RateLimitFilter(AppProperties properties) { this.properties = properties; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) { chain.doFilter(request, response); return; }
        String ip = ClientIpResolver.resolve(request, properties.trustedProxyHops());
        Entry entry = buckets.compute(ip, (key, old) -> old == null ? new Entry(newBucket(), System.nanoTime()) : old.touch());
        evictStale();
        if (!entry.bucket.tryConsume(1)) {
            log.warn("rate limit rejected clientIp={} limitPerMinute={} retryAfterSeconds=60", ip,
                    properties.rateLimitPerMinute());
            response.setStatus(429); response.setHeader("Retry-After", "60"); response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
            return;
        }
        chain.doFilter(request, response);
    }
    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(properties.rateLimitPerMinute(), Refill.greedy(properties.rateLimitPerMinute(), Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }
    private void evictStale() {
        long cutoff = System.nanoTime() - Duration.ofMinutes(10).toNanos();
        buckets.entrySet().removeIf(e -> e.getValue().lastAccess < cutoff);
    }
    private record Entry(Bucket bucket, long lastAccess) { Entry touch() { return new Entry(bucket, System.nanoTime()); } }
}
