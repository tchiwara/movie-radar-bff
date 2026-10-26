package dev.tchiwara.movieradar.config;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpResolver {
    private ClientIpResolver() {}

    /**
     * @param trustedProxyHops how many proxies you control or trust in front of the app.
     *                         0 = no proxy (local run): ignore X-Forwarded-For completely.
     *                         1 = one trusted proxy that appends the client address to the end of the list.
     */
    public static String resolve(HttpServletRequest request, int trustedProxyHops) {
        String remote = request.getRemoteAddr();
        if (trustedProxyHops <= 0) return remote;

        String header = request.getHeader("X-Forwarded-For");
        if (header == null || header.isBlank()) return remote;

        String[] parts = header.split(",");
        int index = parts.length - trustedProxyHops;
        if (index < 0) return remote;           // fewer entries than proxies: don't trust it

        String candidate = parts[index].trim();
        return candidate.isEmpty() ? remote : candidate;
    }
}