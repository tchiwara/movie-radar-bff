package dev.tchiwara.movieradar.root;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of(
                "application", "Movie Radar BFF",
                "status", "running",
                "message", "Welcome! The Movie Radar backend is online.",
                "movies", "/api/movies/popular"
        );
    }
}