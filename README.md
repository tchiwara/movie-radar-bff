# Movie Radar BFF

A Spring Boot backend-for-frontend for [Movie Radar](https://movie-radar-beryl.vercel.app), a React/Vite movie search app ([frontend repo](https://github.com/tchiwara/movie-app)).

The BFF keeps the TMDB and Appwrite credentials on the server, validates and rate-limits browser requests, caches TMDB responses, maps them to compact DTOs, and records search terms in Appwrite for trending analytics.

## Features

- TMDB search and popular-movies proxy with compact DTOs (no raw TMDB payloads)
- Search-term counting and trending via the Appwrite TablesDB REST API
- Input validation, per-IP rate limiting (Bucket4j), Caffeine caching
- Explicit CORS allowlist and trusted-proxy-aware client IP resolution
- Request IDs (`X-Request-Id`), request and upstream logging with credential redaction
- Uniform error contract and upstream timeout/availability mapping
- Fail-fast startup validation of required settings
- Multi-stage Docker image running as a non-root user

## Architecture

[![System context](architecture/system-context.png)](architecture/system-context.png)

*The browser only talks to the BFF. TMDB and Appwrite credentials stay on the server.*

[![Request lifecycle](architecture/request-lifecycle.png)](architecture/request-lifecycle.png)

*Filters, validation, caching, upstream calls and error paths for each endpoint. Click the image to view it full size.*

## API

All endpoints are `GET` under `/api/`.

| Endpoint | Parameters | Notes |
|---|---|---|
| `/api/movies` | `query` (required, non-blank, max 100 chars), `page` (default 1, range 1-500) | TMDB search; query is trimmed and lowercased |
| `/api/movies/popular` | `page` (default 1, range 1-500) | TMDB popular movies |
| `/api/trending` | none | Up to 5 top search terms from Appwrite |

```bash
curl "http://localhost:8080/api/movies?query=batman&page=1"
```

```json
{
  "movies": [
    {
      "id": 268,
      "title": "Batman",
      "posterUrl": "https://image.tmdb.org/t/p/w500/example.jpg",
      "voteAverage": 7.2,
      "originalLanguage": "en",
      "releaseYear": "1989"
    }
  ],
  "page": 1,
  "totalPages": 42
}
```

`/api/trending` returns `[{ "searchTerm", "count", "movieId", "posterUrl" }]`. `movieId` and `posterUrl` can be `null`.

### Errors

Every error uses `{ "status", "code", "message" }`. Upstream response bodies are never forwarded.

| Status | Code | Meaning |
|---:|---|---|
| 400 | `INVALID_INPUT` | Query or page failed validation |
| 429 | `RATE_LIMITED` | Rate limit exceeded (includes `Retry-After: 60`) |
| 503 | `UPSTREAM_UNAVAILABLE` | TMDB unavailable |
| 503 | `UPSTREAM_MISCONFIGURED` | Upstream rejected credentials (401/403) |
| 504 | `UPSTREAM_TIMEOUT` | Upstream request timed out |
| 500 | `INTERNAL_ERROR` | Unexpected error |

## Configuration

All configuration comes from environment variables. See `.env.example` for the names. Never commit real values.

| Variable | Default | Purpose |
|---|---|---|
| `TMDB_TOKEN` | required | TMDB bearer token |
| `APPWRITE_ENDPOINT` | required | Appwrite API endpoint |
| `APPWRITE_PROJECT_ID` | required | Appwrite project ID |
| `APPWRITE_DATABASE_ID` | required | Appwrite database ID |
| `APPWRITE_TABLE_ID` | required | Appwrite table ID |
| `APPWRITE_API_KEY` | required | Server-side Appwrite API key |
| `ALLOWED_ORIGINS` | empty | Comma-separated CORS origins |
| `RATE_LIMIT_PER_MINUTE` | `60` | Requests per minute per client IP |
| `CACHE_TTL_MINUTES` | `10` | Cache expiry after write |
| `TRUSTED_PROXY_HOPS` | `0` | Trusted proxies in front of the app (see below) |
| `PORT` | `8080` | HTTP port |
| `LOG_LEVEL` | `INFO` | Log level for `dev.tchiwara.movieradar` |
| `FORWARD_HEADERS_STRATEGY` | `none` | Spring forward-headers strategy; leave as `none` |

Missing required settings fail startup immediately. Upstream timeouts are currently fixed at 2 s (connect) and 5 s (read).

## Run locally

Set the environment variables above, or copy `.env.example` to `.env` and fill it in (Spring imports `.env` from the working directory). Then:

```powershell
.\mvnw.cmd spring-boot:run      # Windows
./mvnw spring-boot:run          # Linux/macOS
```

```bash
curl "http://localhost:8080/api/movies/popular"
curl "http://localhost:8080/api/trending"
```

## Docker

Two-stage build: a Maven build stage, then a Temurin 17 JRE image running as a non-root `appuser`, started with `-XX:MaxRAMPercentage=75`.

```bash
docker build -t movie-radar-bff .
docker run --rm --name movie-radar-bff --env-file .env -p 8080:8080 movie-radar-bff
```

`.env` is read by Docker at run time and is not baked into the image.

## Testing

```bash
./mvnw test
```

22 tests, all passing, using dummy configuration from `src/test/resources`, so no `.env` or real credentials are needed. They cover: application context startup, controller input validation, global error mapping (400 and 504), TMDB response mapping and timeouts, log redaction, search normalization, non-fatal search-term counting, rate limiting and `X-Forwarded-For` handling, and the Appwrite trending repository (row creation, count increments, error mapping, and the ordered-query fallback). There is no coverage report and no CI yet.

## Caching and rate limiting

**Cache.** Caffeine, in-memory per instance: `movie-search` (key `query:page`) and `popular-movies` (key `page`), 10 min expiry by default, max 500 entries.

**Search counting.** After the response is ready (cache hit or miss), the first returned movie is recorded in Appwrite. It is skipped when the normalized query is shorter than 3 characters or the result list is empty. Counting failures are logged and never fail the search.

**Rate limit.** Bucket4j token bucket per client IP on `/api/**`, 60 requests/minute by default with greedy refill. Rejected requests get `429` with `Retry-After: 60`.

**Client IP.** With `TRUSTED_PROXY_HOPS=0` (default), `X-Forwarded-For` is ignored and the socket address is used, so the header can't be forged to dodge the limit. With N hops, the Nth entry from the right is used. Set it only to the number of proxies you actually control.

## Measured performance

Local Docker run, single requests, not a benchmark:

| Scenario | Time |
|---|---|
| Search, uncached | ~1.8 s |
| Search, cached | ~0.2-0.3 s |
| First request after container start | ~6.25 s (cold upstream connections) |

## Security notes and limitations

**In place:** credentials are server-side only and redacted from logs; CORS is an explicit allowlist (no wildcard, `GET` only); input is bounded before any upstream call; non-root container; forwarded IPs are ignored unless explicitly trusted.

**Limitations:**
- Cache and rate-limit buckets are per instance, not shared across replicas.
- Appwrite counting is read-then-write, so concurrent increments can lose updates.
- Counting runs synchronously on the request path, adding latency to search, including cache hits.
- `/api/trending` returns an empty list on most Appwrite failures instead of an error.
- No authentication on the API, and no actuator/health endpoint or metrics.

## Roadmap

Health endpoint and metrics, counting off the request path, env-configurable timeouts, CI (tests + image build), integration tests for the full filter/controller flow.