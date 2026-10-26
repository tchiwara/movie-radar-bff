# movie-radar-bff

A Spring Boot backend-for-frontend for a React movie search UI. TMDB and Appwrite credentials remain server-side; the browser receives only UI-specific DTOs.

| Method | Path | Parameters | Purpose |
|---|---|---|---|
| GET | `/api/movies` | `query`, `page` (1-500) | Search TMDB |
| GET | `/api/movies/popular` | `page` (1-500) | Popular TMDB movies |
| GET | `/api/trending` | none | Top five search terms |

## Environment

Copy `.env.example` to `.env` for local reference (the application reads environment variables, not the file automatically).

`TMDB_TOKEN`, `ALLOWED_ORIGINS` (comma-separated, no wildcard), `RATE_LIMIT_PER_MINUTE` (default 60), `CACHE_TTL_MINUTES` (default 10), `APPWRITE_ENDPOINT`, `APPWRITE_PROJECT_ID`, `APPWRITE_DATABASE_ID`, `APPWRITE_TABLE_ID`, `APPWRITE_API_KEY`, and `PORT` (default 8080).

## Run and test

Set the variables in your shell, then run `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`). Run tests with `./mvnw test` or `mvnw.cmd test`.

## Security decisions

The TMDB bearer token and Appwrite API key are never returned to clients. CORS is an explicit origin allowlist. Input is bounded before upstream calls, failures do not expose upstream bodies or stack traces, and the servlet token bucket limits each forwarded client IP. Spring's framework forward-header strategy is enabled for Render's proxy headers; deploy only behind a trusted proxy. Appwrite increments use a read-then-write operation because the REST shape used here has no conditional atomic increment, so concurrent increments can be lost.

Bucket4j is included as `com.bucket4j:bucket4j_jdk17-core:8.21.0` (the current Maven Central release checked during setup); its Java package is `io.github.bucket4j`.
