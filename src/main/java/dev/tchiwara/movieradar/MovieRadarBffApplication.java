package dev.tchiwara.movieradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class MovieRadarBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(MovieRadarBffApplication.class, args);
    }
}
