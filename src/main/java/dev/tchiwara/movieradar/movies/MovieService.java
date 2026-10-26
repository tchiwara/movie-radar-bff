package dev.tchiwara.movieradar.movies;

import dev.tchiwara.movieradar.trending.TrendingRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.tchiwara.movieradar.trending.CountResult;

@Service
public class MovieService {
    private static final Logger log = LoggerFactory.getLogger(MovieService.class);
    private final MovieCache cache; private final TrendingRepository trending; private final SearchNormalizer normalizer;
    public MovieService(MovieCache cache, TrendingRepository trending, SearchNormalizer normalizer) {
        this.cache = cache; this.trending = trending; this.normalizer = normalizer;
    }
    public MovieResponse search(String rawQuery, int page) {
        String query = normalizer.normalize(rawQuery);
        MovieResponse response = cache.search(query, page);
        if (normalizer.isCountable(query)) {
            try {
                MovieDto first = response.movies().stream().findFirst().orElse(null);
                if (first == null) return response;
                CountResult result = trending.increment(query, first.id(), first.posterUrl());
                log.info("search term counted term={} action={}", query, result == CountResult.NEW ? "new" : "incremented");
            } catch (RuntimeException e) {
                log.warn("search term counting failed term={} exceptionClass={} message={}",
                        query, e.getClass().getName(), e.getMessage());
            }
        }
        return response;
    }
    public MovieResponse popular(int page) { return cache.popular(page); }
}
