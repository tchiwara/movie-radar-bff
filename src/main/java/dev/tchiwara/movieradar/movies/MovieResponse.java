package dev.tchiwara.movieradar.movies;

import java.util.List;
public record MovieResponse(List<MovieDto> movies, int page, int totalPages) {
    public MovieResponse {
        totalPages = Math.min(totalPages, 500);
    }
}
