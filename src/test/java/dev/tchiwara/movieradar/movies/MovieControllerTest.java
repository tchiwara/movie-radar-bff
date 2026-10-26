package dev.tchiwara.movieradar.movies;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = MovieController.class, properties = {
        "app.tmdb-token=test-token",
        "app.appwrite.endpoint=https://example.com",
        "app.appwrite.project-id=test-project",
        "app.appwrite.database-id=test-database",
        "app.appwrite.table-id=test-table",
        "app.appwrite.api-key=test-key"
})
class MovieControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MovieService service;
    @Test void invalidPageReturns400() throws Exception {
        mvc.perform(get("/api/movies").param("query", "batman").param("page", "0"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
