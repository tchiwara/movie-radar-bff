package dev.tchiwara.movieradar.movies;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/movies")
public class MovieController {
    private final MovieService service;
    public MovieController(MovieService service) { this.service = service; }
    @GetMapping
    public MovieResponse search(@RequestParam String query, @RequestParam(defaultValue = "1") int page) {
        if (query.trim().isEmpty() || query.length() > 100 || page < 1 || page > 500)
            throw new IllegalArgumentException("query and page are invalid");
        return service.search(query, page);
    }
    @GetMapping("/popular")
    public MovieResponse popular(@RequestParam(defaultValue = "1") int page) {
        if (page < 1 || page > 500) throw new IllegalArgumentException("page is invalid");
        return service.popular(page);
    }
}
