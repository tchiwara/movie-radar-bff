package dev.tchiwara.movieradar.trending;

import java.util.List;
public interface TrendingRepository {
    CountResult increment(String searchTerm, int movieId, String posterUrl);
    List<TrendingTerm> topFive();
}
