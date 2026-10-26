package dev.tchiwara.movieradar.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StartupLogging {
    private static final Logger log = LoggerFactory.getLogger(StartupLogging.class);
    private final AppProperties properties;

    @Value("${server.forward-headers-strategy:framework}")
    private String forwardHeadersStrategy;

    public StartupLogging(AppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void logConfiguration() {
        AppProperties.Appwrite a = properties.appwrite();
        log.info("configuration TMDB_TOKEN={} APPWRITE_ENDPOINT={} APPWRITE_PROJECT_ID={} " +
                        "APPWRITE_DATABASE_ID={} APPWRITE_TABLE_ID={} APPWRITE_API_KEY={} " +
                        "ALLOWED_ORIGINS={} rateLimitPerMinute={} cacheTtlMinutes={} " +
                        "trustedProxyHops={} connectTimeoutSeconds=2 readTimeoutSeconds=5 forwardHeadersStrategy={}",
                state(nonBlank(properties.tmdbToken())), state(a != null && nonBlank(a.endpoint())), state(a != null && nonBlank(a.projectId())),
                state(a != null && nonBlank(a.databaseId())), state(a != null && nonBlank(a.tableId())),
                state(a != null && nonBlank(a.apiKey())), state(!properties.origins().isEmpty()),
                properties.rateLimitPerMinute(), properties.cacheTtlMinutes(), properties.trustedProxyHops(),
                forwardHeadersStrategy);
        if (properties.tmdbToken() == null || properties.tmdbToken().isBlank()) {
            throw new IllegalStateException("TMDB_TOKEN is missing; configure the TMDB server credential before startup");
        }
        if (a == null || !nonBlank(a.endpoint()) || !nonBlank(a.projectId()) || !nonBlank(a.databaseId())
                || !nonBlank(a.tableId()) || !nonBlank(a.apiKey())) {
            throw new IllegalStateException("Appwrite configuration is incomplete; set APPWRITE_ENDPOINT, " +
                    "APPWRITE_PROJECT_ID, APPWRITE_DATABASE_ID, APPWRITE_TABLE_ID, and APPWRITE_API_KEY");
        }
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String state(boolean set) {
        return set ? "set" : "MISSING";
    }
}
