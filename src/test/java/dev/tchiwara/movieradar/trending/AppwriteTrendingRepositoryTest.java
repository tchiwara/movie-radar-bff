package dev.tchiwara.movieradar.trending;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import dev.tchiwara.movieradar.config.AppProperties;
import dev.tchiwara.movieradar.error.UpstreamException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AppwriteTrendingRepositoryTest {
    private static final AppProperties PROPERTIES = new AppProperties("", "", 60, 10, 0,
            new AppProperties.Appwrite("https://appwrite.test/v1", "project-id", "database-id", "table-id", "api-key"));
    private static final String ROWS = "https://appwrite.test/v1/tablesdb/database-id/tables/table-id/rows";

    @Test
    void createsRowWithDerivedIdAndFirstMovieMetadata() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AppwriteTrendingRepository repository = new AppwriteTrendingRepository(builder.build(), PROPERTIES, new ObjectMapper());

        server.expect(requestTo(ROWS + "/movie-42"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());
        server.expect(requestTo(ROWS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"rowId\":\"movie-42\""),
                        org.hamcrest.Matchers.containsString("\"searchTerm\":\"a\\\"b[\""),
                        org.hamcrest.Matchers.containsString("\"movie_id\":42"),
                        org.hamcrest.Matchers.containsString("\"poster_url\":\"https://img.test/poster.jpg\""))))
                // Real Appwrite answers 201 Created for a new row.
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertEquals(CountResult.NEW, repository.increment("a\"b[", 42, "https://img.test/poster.jpg"));
        server.verify();
    }

    @Test
    void updatesOnlyTheCountOfTheExistingMovieRow() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AppwriteTrendingRepository repository = new AppwriteTrendingRepository(builder.build(), PROPERTIES, new ObjectMapper());

        server.expect(requestTo(ROWS + "/movie-9"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"$id\":\"movie-9\",\"searchTerm\":\"Old Title\",\"count\":4,\"movie_id\":9,\"poster_url\":\"https://img.test/old.jpg\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(ROWS + "/movie-9"))
                // PATCH changes only the fields sent; PUT would replace the whole row.
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().string("{\"data\":{\"count\":5}}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertEquals(CountResult.INCREMENTED, repository.increment("new label", 9, "https://img.test/new.jpg"));
        server.verify();
    }

    @Test
    void createConflictFallsBackToIncrementingTheRowAnotherRequestCreated() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AppwriteTrendingRepository repository = new AppwriteTrendingRepository(builder.build(), PROPERTIES, new ObjectMapper());

        server.expect(requestTo(ROWS + "/movie-7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());
        server.expect(requestTo(ROWS))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"document_already_exists\",\"message\":\"exists\"}"));
        server.expect(requestTo(ROWS + "/movie-7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"$id\":\"movie-7\",\"searchTerm\":\"Seven\",\"count\":1,\"movie_id\":7,\"poster_url\":\"https://img.test/7.jpg\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(ROWS + "/movie-7"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().string("{\"data\":{\"count\":2}}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertEquals(CountResult.INCREMENTED, repository.increment("Seven", 7, "https://img.test/7.jpg"));
        server.verify();
    }

    @Test
    void missingRowLookupIsNotLoggedAsAFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AppwriteTrendingRepository repository = new AppwriteTrendingRepository(builder.build(), PROPERTIES, new ObjectMapper());
        Logger logger = (Logger) LoggerFactory.getLogger(AppwriteTrendingRepository.class);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        server.expect(requestTo(ROWS + "/movie-3")).andRespond(withResourceNotFound());
        server.expect(requestTo(ROWS)).andRespond(withStatus(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON).body("{}"));
        repository.increment("Three", 3, "https://img.test/3.jpg");

        assertTrue(appender.list.stream().noneMatch(event -> event.getLevel() == Level.WARN));
        logger.detachAppender(appender);
    }

    @Test
    void mapsUnauthorizedAndServerErrorStatuses() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AppwriteTrendingRepository repository = new AppwriteTrendingRepository(builder.build(), PROPERTIES, new ObjectMapper());

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/tablesdb/")))
                .andRespond(request -> {
                    MockClientHttpResponse response = new MockClientHttpResponse(
                            "{\"type\":\"general_unauthorized_scope\",\"message\":\"bad key\"}"
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8), HttpStatus.UNAUTHORIZED);
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    return response;
                });
        UpstreamException unauthorized = assertThrows(UpstreamException.class,
                () -> repository.increment("abc", 1, "https://img.test/poster.jpg"));
        assertEquals("misconfiguration", unauthorized.failureKind());
        assertEquals("general_unauthorized_scope", unauthorized.upstreamType());
        assertEquals("bad key", unauthorized.upstreamMessage());

        server.reset();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/tablesdb/")))
                .andRespond(withServerError());
        UpstreamException serverError = assertThrows(UpstreamException.class,
                () -> repository.increment("abc", 1, "https://img.test/poster.jpg"));
        assertEquals("error-status", serverError.failureKind());
        assertEquals(500, serverError.status());
    }

    @Test
    void asksForDescendingOrderAndFiveRows() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AppwriteTrendingRepository repository = new AppwriteTrendingRepository(builder.build(), PROPERTIES, new ObjectMapper());

        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("orderDesc"),
                        org.hamcrest.Matchers.containsString("limit"))))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"rows\":[{\"$id\":\"1\",\"searchTerm\":\"a\",\"count\":9,\"movie_id\":42,\"poster_url\":\"https://img.test/a.jpg\"},{\"$id\":\"2\",\"searchTerm\":\"b\",\"count\":8}]}",
                        MediaType.APPLICATION_JSON));

        assertEquals(List.of(new TrendingTerm("a", 9, 42, "https://img.test/a.jpg"), new TrendingTerm("b", 8, null, null)),
                repository.topFive());
        server.verify();
    }

    @Test
    void topFiveReturnsEmptyListWhenKeyIsRejected() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AppwriteTrendingRepository repository = new AppwriteTrendingRepository(builder.build(), PROPERTIES, new ObjectMapper());

        // A 401 must not trigger the in-memory fallback: only one request is expected.
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/tablesdb/")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"general_unauthorized_scope\",\"message\":\"bad key\"}"));

        assertEquals(List.of(), repository.topFive());
        server.verify();
    }

    @Test
    void topFiveFallsBackToInMemorySortWhenServerRejectsTheQuery() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AppwriteTrendingRepository repository = new AppwriteTrendingRepository(builder.build(), PROPERTIES, new ObjectMapper());

        server.expect(requestTo(org.hamcrest.Matchers.containsString("orderDesc")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"general_query_invalid\",\"message\":\"unsupported query\"}"));
        server.expect(requestTo(ROWS))
                .andRespond(withSuccess("{\"rows\":[{\"$id\":\"1\",\"searchTerm\":\"low\",\"count\":1},{\"$id\":\"2\",\"searchTerm\":\"high\",\"count\":9}]}",
                        MediaType.APPLICATION_JSON));

        assertEquals(List.of(new TrendingTerm("high", 9, null, null), new TrendingTerm("low", 1, null, null)),
                repository.topFive());
        server.verify();
    }

    @Test
    void logsNeverContainAppwriteSecretsOrIdentifiers() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AppwriteTrendingRepository repository = new AppwriteTrendingRepository(builder.build(), PROPERTIES, new ObjectMapper());
        Logger logger = (Logger) LoggerFactory.getLogger(AppwriteTrendingRepository.class);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/tablesdb/")))
                .andRespond(withSuccess("{\"rows\":[]}", MediaType.APPLICATION_JSON));
        repository.topFive();

        String output = appender.list.stream()
                .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).reduce("", String::concat);
        assertFalse(output.contains("api-key"));
        assertFalse(output.contains("project-id"));
        assertFalse(output.contains("database-id"));
        assertFalse(output.contains("table-id"));
        logger.detachAppender(appender);
    }
}