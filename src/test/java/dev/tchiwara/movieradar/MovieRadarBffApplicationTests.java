package dev.tchiwara.movieradar;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "app.tmdb-token=test-token",
        "app.appwrite.endpoint=https://example.com",
        "app.appwrite.project-id=test-project",
        "app.appwrite.database-id=test-database",
        "app.appwrite.table-id=test-table",
        "app.appwrite.api-key=test-key"
})
class MovieRadarBffApplicationTests {
    @Test void contextLoads() {}
}
