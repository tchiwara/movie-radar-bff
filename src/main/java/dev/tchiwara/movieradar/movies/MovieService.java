package dev.tchiwara.movieradar.movies;

import dev.tchiwara.movieradar.trending.CountResult;
import dev.tchiwara.movieradar.trending.TrendingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MovieService {
    private static final Logger log = LoggerFactory.getLogger(MovieService.class);
    private final MovieCache cache;
    private final TrendingRepository trending;
    private final SearchNormalizer normalizer;
    private final CountDeduplicator deduplicator;

    @Autowired
    public MovieService(MovieCache cache, TrendingRepository trending, SearchNormalizer normalizer,
                        CountDeduplicator deduplicator) {
        this.cache = cache;
        this.trending = trending;
        this.normalizer = normalizer;
        this.deduplicator = deduplicator;
    }

    public MovieService(MovieCache cache, TrendingRepository trending, SearchNormalizer normalizer) {
        this(cache, trending, normalizer, new CountDeduplicator(10));
    }

    public MovieResponse search(String rawQuery, int page) {
        return search(rawQuery, page, null);
    }

    public MovieResponse search(String rawQuery, int page, String clientIp) {
        String query = normalizer.normalize(rawQuery);
        MovieResponse response = cache.search(query, page);
        if (normalizer.isCountable(query)) {
            MovieDto first = null;
            try {
                first = response.movies().stream().findFirst().orElse(null);
                if (first == null) return response;
                if (clientIp != null && !deduplicator.firstInWindow(clientIp, first.id())) {
                    log.debug("search not counted, movie already counted recently term={}", query);
                    return response;
                }
                String label = first.title() == null || first.title().isBlank() ? query : first.title();
                CountResult result = trending.increment(label, first.id(), first.posterUrl());
                log.info("search counted term={} movieId={} action={}", query, first.id(),
                        result == CountResult.NEW ? "new" : "incremented");
            } catch (RuntimeException e) {
                if (first != null && clientIp != null) deduplicator.forget(clientIp, first.id());
                log.warn("search term counting failed term={} exceptionClass={} message={}",
                        query, e.getClass().getName(), e.getMessage());
            }
        }
        return response;
    }

    public MovieResponse popular(int page) { return cache.popular(page); }
}