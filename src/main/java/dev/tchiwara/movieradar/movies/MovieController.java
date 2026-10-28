package dev.tchiwara.movieradar.movies;

import dev.tchiwara.movieradar.config.AppProperties;
import dev.tchiwara.movieradar.config.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/movies")
public class MovieController {
    private final MovieService service;
    private final AppProperties properties;

    public MovieController(MovieService service, AppProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping
    public MovieResponse search(@RequestParam String query, @RequestParam(defaultValue = "1") int page,
                                HttpServletRequest request) {
        if (query.trim().isEmpty() || query.length() > 100 || page < 1 || page > 500)
            throw new IllegalArgumentException("query and page are invalid");
        String clientIp = ClientIpResolver.resolve(request, properties.trustedProxyHops());
        return service.search(query, page, clientIp);
    }

    @GetMapping("/popular")
    public MovieResponse popular(@RequestParam(defaultValue = "1") int page) {
        if (page < 1 || page > 500) throw new IllegalArgumentException("page is invalid");
        return service.popular(page);
    }
}