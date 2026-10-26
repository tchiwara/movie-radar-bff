package dev.tchiwara.movieradar.movies;

import dev.tchiwara.movieradar.error.UpstreamException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

@Component
public class MovieClient {
    private static final Logger log = LoggerFactory.getLogger(MovieClient.class);
    private final RestClient client;
    private final String token;
    private final ObjectMapper objectMapper;
    public MovieClient(RestClient client, @Value("${app.tmdb-token}") String token, ObjectMapper objectMapper) {
        this.client = client; this.token = token; this.objectMapper = objectMapper;
    }
    public MovieResponse search(String query, int page) { return fetch("search-movies", "/search/movie?query={q}&page={p}", query, page); }
    public MovieResponse popular(int page) { return fetch("popular-movies", "/movie/popular?page={p}", null, page); }
    private MovieResponse fetch(String operation, String path, String query, int page) {
        long started = System.nanoTime();
        try {
            TmdbResponse body = client.get().uri("https://api.themoviedb.org/3" + path, query, page)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new UpstreamException("TMDB returned an error status", false,
                                    response.getStatusCode().value(), "error-status", null);
                        }
                        try {
                            TmdbResponse parsed = objectMapper.readValue(response.getBody(), TmdbResponse.class);
                            log.info("upstream target=TMDB operation={} status={} durationMs={}", operation,
                                    response.getStatusCode().value(), elapsedMs(started));
                            return parsed;
                        } catch (java.io.IOException e) {
                            throw new IllegalStateException("Invalid TMDB response", e);
                        }
                    });
            if (body == null) throw new UpstreamException("TMDB returned no response", false);
            return new MovieResponse(body.results == null ? List.of() : body.results.stream().map(this::map).toList(),
                    body.page, body.totalPages);
        } catch (UpstreamException e) {
            logFailure(operation, "GET", pathWithoutQuery(path), e, started);
            throw e;
        }
        catch (org.springframework.web.client.RestClientException e) {
            boolean timeout = isTimeout(e);
            int status = e instanceof org.springframework.web.client.RestClientResponseException r
                    ? r.getStatusCode().value() : 0;
            String kind = timeout ? "timeout" : status != 0 ? "error-status" : "connection-failure";
            logFailure(operation, "GET", pathWithoutQuery(path), e, started);
            throw new UpstreamException(timeout ? "TMDB timed out" : "TMDB is unavailable",
                    timeout, status, kind, e);
        }
    }
    private void logFailure(String operation, String method, String path, Throwable error, long started) {
        Throwable root = rootCause(error);
        int status = error instanceof UpstreamException upstream ? upstream.status()
                : error instanceof org.springframework.web.client.RestClientResponseException response
                ? response.getStatusCode().value() : 0;
        String kind = error instanceof UpstreamException upstream ? upstream.failureKind()
                : isTimeout(error) ? "timeout" : status != 0 ? "error-status" : "connection-failure";
        String message = safeMessage(root, token);
        String reason = "connection-failure".equals(kind) ? "connection failed" : message;
        log.warn("upstream failure target=TMDB operation={} method={} path={} rootExceptionClass={} rootMessage={} " +
                        "status={} kind={} durationMs={} reason={}",
                operation, method, path, root.getClass().getName(), message, status, kind, elapsedMs(started), reason);
    }
    private static Throwable rootCause(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        return root;
    }
    private static String pathWithoutQuery(String path) {
        int queryStart = path.indexOf('?');
        return queryStart < 0 ? path : path.substring(0, queryStart);
    }
    static String safeMessage(Throwable error, String secret) {
        String message = error.getMessage();
        return message == null ? "" : message.replace(secret, "[REDACTED]").replaceAll("[\\r\\n]", " ");
    }
    private static long elapsedMs(long started) { return (System.nanoTime() - started) / 1_000_000; }
    static boolean isTimeout(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof java.net.SocketTimeoutException
                    || current instanceof java.net.http.HttpConnectTimeoutException
                    || current instanceof java.net.http.HttpTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException)
                return true;
        }
        return false;
    }
    private MovieDto map(TmdbMovie m) {
        String year = m.releaseDate == null || m.releaseDate.length() < 4 ? "" : m.releaseDate.substring(0, 4);
        return new MovieDto(m.id, m.title, m.posterPath == null ? "" : "https://image.tmdb.org/t/p/w500" + m.posterPath,
                m.voteAverage, m.originalLanguage, year);
    }
    static class TmdbResponse {
        public int page;
        @JsonProperty("total_pages") public int totalPages;
        public List<TmdbMovie> results;
    }
    static class TmdbMovie {
        public int id; public String title;
        @JsonProperty("poster_path") public String posterPath;
        @JsonProperty("original_language") public String originalLanguage;
        @JsonProperty("release_date") public String releaseDate;
        @JsonProperty("vote_average") public double voteAverage;
    }
}
