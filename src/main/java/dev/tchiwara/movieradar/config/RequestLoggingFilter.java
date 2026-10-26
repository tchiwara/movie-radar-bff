package dev.tchiwara.movieradar.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID = "requestId";
    private final AppProperties properties;

    public RequestLoggingFilter(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long started = System.nanoTime();
        MDC.put(REQUEST_ID, requestId);
        response.setHeader("X-Request-Id", requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            org.slf4j.LoggerFactory.getLogger(RequestLoggingFilter.class).info(
                    "request completed method={} path={} status={} durationMs={} clientIp={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs,
                    ClientIpResolver.resolve(request, properties.trustedProxyHops()));
            MDC.remove(REQUEST_ID);
        }
    }
}
