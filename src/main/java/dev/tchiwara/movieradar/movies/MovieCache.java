package dev.tchiwara.movieradar.movies;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MovieCache {
    private static final Logger log = LoggerFactory.getLogger(MovieCache.class);
    private final MovieClient client;
    private final CacheManager cacheManager;
    public MovieCache(MovieClient client, CacheManager cacheManager) {
        this.client = client; this.cacheManager = cacheManager;
    }
    public MovieResponse search(String query, int page) {
        String key = query + ":" + page;
        var cache = cacheManager.getCache("movie-search");
        MovieResponse cached = cache == null ? null : cache.get(key, MovieResponse.class);
        if (cached != null) { log.debug("cache hit cache=movie-search key={}", key); return cached; }
        log.debug("cache miss cache=movie-search key={}", key);
        MovieResponse result = client.search(query, page);
        if (cache != null && result != null) cache.put(key, result);
        return result;
    }
    public MovieResponse popular(int page) {
        String key = String.valueOf(page);
        var cache = cacheManager.getCache("popular-movies");
        MovieResponse cached = cache == null ? null : cache.get(key, MovieResponse.class);
        if (cached != null) { log.debug("cache hit cache=popular-movies key={}", key); return cached; }
        log.debug("cache miss cache=popular-movies key={}", key);
        MovieResponse result = client.popular(page);
        if (cache != null && result != null) cache.put(key, result);
        return result;
    }
}
