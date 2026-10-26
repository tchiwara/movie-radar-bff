package dev.tchiwara.movieradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String tmdbToken, String allowedOrigins, int rateLimitPerMinute,
                            int cacheTtlMinutes, int trustedProxyHops, Appwrite appwrite) {
    public List<String> origins() {
        return allowedOrigins == null ? List.of() : java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
    }
    public record Appwrite(String endpoint, String projectId, String databaseId, String tableId, String apiKey) {}
}
