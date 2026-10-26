package dev.tchiwara.movieradar.movies;

public record MovieDto(int id, String title, String posterUrl, double voteAverage,
                       String originalLanguage, String releaseYear) {}
