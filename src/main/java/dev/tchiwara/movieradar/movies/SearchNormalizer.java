package dev.tchiwara.movieradar.movies;

import org.springframework.stereotype.Component;

@Component
public class SearchNormalizer {
    public String normalize(String query) {
        if (query == null) return "";
        return query.trim().toLowerCase(java.util.Locale.ROOT);
    }
    public boolean isCountable(String query) { return normalize(query).length() >= 3; }
}
