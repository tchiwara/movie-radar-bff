package dev.tchiwara.movieradar.trending;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.tchiwara.movieradar.config.AppProperties;
import dev.tchiwara.movieradar.error.UpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Repository
public class AppwriteTrendingRepository implements TrendingRepository {
    private static final Logger log = LoggerFactory.getLogger(AppwriteTrendingRepository.class);
    private final RestClient client;
    private final AppProperties.Appwrite config;
    private final ObjectMapper objectMapper;

    public AppwriteTrendingRepository(RestClient client, AppProperties properties, ObjectMapper objectMapper) {
        this.client = client;
        this.config = properties.appwrite();
        this.objectMapper = objectMapper;
    }

    /**
     * One row per movie: the row id is derived from the movie id, so Appwrite itself refuses a second
     * row for the same movie. The {@code searchTerm} column stores the label passed in by the caller
     * (the movie title) when the row is first created.
     */
    @Override
    public CountResult increment(String searchTerm, int movieId, String posterUrl) {
        String rowsPath = rowsPath();
        String rowId = rowIdFor(movieId);
        String rowPath = rowsPath + "/" + rowId;

        Row existing = findRow(rowPath);
        if (existing != null) {
            increase(rowPath, existing);
            return CountResult.INCREMENTED;
        }

        validatePosterUrl(posterUrl);
        try {
            exchange("POST", "create-movie-row", rowsPath,
                    Map.of("rowId", rowId,
                            "data", Map.of("searchTerm", searchTerm, "count", 1,
                                    "movie_id", movieId, "poster_url", posterUrl)),
                    Void.class);
            return CountResult.NEW;
        } catch (UpstreamException e) {
            // 409: another request created the row between our lookup and our create.
            if (e.status() != 409) throw e;
        }

        Row created = findRow(rowPath);
        if (created == null) {
            throw new IllegalStateException("Appwrite row missing after a create conflict");
        }
        increase(rowPath, created);
        return CountResult.INCREMENTED;
    }

    private Row findRow(String rowPath) {
        try {
            return exchange("GET", "find-movie-row", rowPath, null, Row.class);
        } catch (UpstreamException e) {
            if (e.status() == 404) return null;
            throw e;
        }
    }

    private void increase(String rowPath, Row row) {
        // VERIFY: Appwrite documents row reads and updates but no atomic numeric-column increment.
        // This read-then-write sequence can lose concurrent increments.
        // PATCH changes only the fields sent; PUT would replace the whole row.
        exchange("PATCH", "increment-movie-row", rowPath,
                Map.of("data", Map.of("count", row.count() + 1)), Void.class);
    }

    private static String rowIdFor(int movieId) {
        return "movie-" + movieId;
    }

    @Override
    public List<TrendingTerm> topFive() {
        String rowsPath = rowsPath();
        String queryPath = rowsPath + "?queries[]=" +
                encode(query("orderDesc", "count", null)) + "&queries[]=" +
                encode(query("limit", null, List.of(5)));
        try {
            RowList result = exchange("GET", "top-trending", queryPath, null, RowList.class);
            return toTrending(result == null ? null : result.rows);
        } catch (UpstreamException e) {
            // Only a rejected query (400) is worth retrying without server-side ordering.
            // Auth, scope and network failures are already logged; trending degrades to empty.
            if (e.status() != 400) return List.of();
        } catch (RuntimeException e) {
            return List.of();
        }
        try {
            RowList result = exchange("GET", "top-trending-fallback", rowsPath, null, RowList.class);
            return toTrending(result == null ? null : result.rows).stream()
                    .sorted(Comparator.comparingInt(TrendingTerm::count).reversed())
                    .limit(5).toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private List<TrendingTerm> toTrending(List<Row> rows) {
        return rows == null ? List.of() : rows.stream()
                .map(row -> new TrendingTerm(row.searchTerm(), row.count(), row.movieId(),
                        row.posterUrl() == null || row.posterUrl().isBlank() ? null : row.posterUrl())).toList();
    }

    private <T> T exchange(String method, String operation, String path, Object body, Class<T> responseType) {
        long started = System.nanoTime();
        try {
            RestClient.RequestHeadersSpec<?> request = switch (method) {
                case "GET" -> client.get().uri(java.net.URI.create(path));
                case "PATCH" -> client.patch().uri(java.net.URI.create(path)).body(body);
                case "POST" -> client.post().uri(java.net.URI.create(path)).body(body);
                default -> throw new IllegalArgumentException("Unsupported Appwrite method");
            };
            return request.headers(this::headers).exchange((req, response) ->
                    readResponse(method, response.getStatusCode(), operation, response, responseType, started));
        } catch (RuntimeException e) {
            // A missing row is the normal "first search for this movie" case, not a failure.
            if (!isExpectedMissingRow(operation, e)) logFailure(operation, method, e, started);
            throw e;
        }
    }

    private static boolean isExpectedMissingRow(String operation, RuntimeException error) {
        return "find-movie-row".equals(operation)
                && error instanceof UpstreamException upstream && upstream.status() == 404;
    }

    private <T> T readResponse(String method, HttpStatusCode status, String operation,
                               org.springframework.http.client.ClientHttpResponse response,
                               Class<T> responseType, long started) {
        try {
            if (status.isError()) {
                String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                ErrorBody error = parseError(body);
                String kind = status.value() == 401 || status.value() == 403
                        ? "misconfiguration" : "error-status";
                throw new UpstreamException("Appwrite returned an error status", false, status.value(),
                        kind, null, truncate(error.type()), truncate(error.message()));
            }
            log.info("upstream target=APPWRITE operation={} resource=rows method={} status={} durationMs={}",
                    operation, method, status.value(), elapsedMs(started));
            if (responseType == Void.class) return null;
            return objectMapper.readValue(response.getBody(), responseType);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid Appwrite response", e);
        }
    }

    private ErrorBody parseError(String body) {
        try {
            return objectMapper.readValue(body, ErrorBody.class);
        } catch (RuntimeException ignored) {
            return new ErrorBody();
        }
    }

    private String query(String method, String attribute, List<?> values) {
        Map<String, Object> query = new java.util.LinkedHashMap<>();
        query.put("method", method);
        if (attribute != null) query.put("attribute", attribute);
        if (values != null) query.put("values", values);
        try {
            return objectMapper.writeValueAsString(query);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Could not build Appwrite query", e);
        }
    }

    private void validatePosterUrl(String posterUrl) {
        try {
            java.net.URI uri = java.net.URI.create(posterUrl);
            if (uri.getHost() == null || (!"http".equalsIgnoreCase(uri.getScheme())
                    && !"https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("poster URL must use HTTP or HTTPS");
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("poster URL must be a valid URL", e);
        }
    }

    private void headers(HttpHeaders headers) {
        headers.set("X-Appwrite-Project", config.projectId());
        headers.set("X-Appwrite-Key", config.apiKey());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    }

    private void logFailure(String operation, String method, RuntimeException error, long started) {
        Throwable root = rootCause(error);
        int status = error instanceof UpstreamException upstream ? upstream.status() : 0;
        String kind = failureKind(error);
        String message = error instanceof UpstreamException upstream && upstream.upstreamMessage() != null
                ? upstream.upstreamMessage() : safeMessage(root);
        String type = error instanceof UpstreamException upstream ? upstream.upstreamType() : null;
        log.warn("upstream failure target=APPWRITE operation={} resource=rows method={} status={} kind={} " +
                        "durationMs={} rootExceptionClass={} rootMessage={} errorType={}",
                operation, method, status, kind, elapsedMs(started), root.getClass().getName(),
                "connection-failure".equals(kind) ? "connection failed" : message, type);
    }

    private String failureKind(RuntimeException error) {
        if (error instanceof UpstreamException upstream) return upstream.failureKind();
        if (isTimeout(error)) return "timeout";
        if (error instanceof IllegalArgumentException) return "invalid-request";
        if (error instanceof IllegalStateException) return "invalid-response";
        return "connection-failure";
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof java.net.SocketTimeoutException
                    || current instanceof java.net.http.HttpConnectTimeoutException
                    || current instanceof java.net.http.HttpTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException) return true;
        }
        return false;
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null) return "";
        return (config.apiKey() == null || config.apiKey().isBlank()
                ? message : message.replace(config.apiKey(), "[REDACTED]"))
                .replaceAll("[\\r\\n]", " ")
                .replaceAll("(?i)(authorization|x-appwrite-key)=[^ ]+", "$1=[REDACTED]");
    }

    private String rowsPath() {
        return config.endpoint() + "/tablesdb/" + config.databaseId() + "/tables/" + config.tableId() + "/rows";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        return root;
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RowList {
        public List<Row> rows;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Row {
        @JsonProperty("$id") public String id;
        public String searchTerm;
        public int count;
        public Integer movie_id;
        public String poster_url;
        String id() { return id; }
        String searchTerm() { return searchTerm; }
        int count() { return count; }
        Integer movieId() { return movie_id; }
        String posterUrl() { return poster_url; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ErrorBody {
        public String type;
        public String message;
        String type() { return type; }
        String message() { return message; }
    }
}