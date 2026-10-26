package dev.tchiwara.movieradar.trending;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class TrendingController {
    private final TrendingRepository repository;
    public TrendingController(TrendingRepository repository) { this.repository = repository; }
    @GetMapping("/api/trending")
    public List<TrendingTerm> trending() { return repository.topFive(); }
}
