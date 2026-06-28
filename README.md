# Equity Analytics API

A Kotlin/Ktor REST API for financial analytics: daily returns, Jensen's alpha, volatility, beta, Sharpe ratio, Sortino ratio, Calmar ratio, maximum drawdown, and rolling correlation. Uses Alpha Vantage for real market data (or mock data when no API key is set).

> **Portfolio Project:** Demonstration of production-grade financial analytics API engineering. Source code available for review only. See [LICENSE](LICENSE) and [docs/NOTICE.md](docs/NOTICE.md) for terms. Repository: [github.com/carjam/equity-analytics-api](https://github.com/carjam/equity-analytics-api)

**All calculations use close-of-day (close-of-date) prices only:** one price per calendar day per ticker (the daily closing price). Returns are day-over-day close-to-close; volatility, alpha, beta, Sharpe, Sortino, Calmar, and correlation are derived from those daily close-based returns. Maximum drawdown is computed directly from close-of-day prices.

## Quick Start

1. **Build and run** (mock data; no API key required):
   ```bash
   ./gradlew run
   ```
   On Windows: `.\gradlew.bat run`

2. **Server** starts at [http://localhost:8080](http://localhost:8080).

3. **Health check**:
   ```bash
   curl http://localhost:8080/health
   ```

## Alpha Vantage API Key (Real Data)

To use live market data:

1. Get a free API key: [https://www.alphavantage.co/support/#api-key](https://www.alphavantage.co/support/#api-key)
2. Set the environment variable:
   - **Linux/macOS**: `export ALPHA_VANTAGE_API_KEY=your_key_here`
   - **Windows (PowerShell)**: `$env:ALPHA_VANTAGE_API_KEY="your_key_here"`
   - **Windows (CMD)**: `set ALPHA_VANTAGE_API_KEY=your_key_here`
3. Run the server: `./gradlew run`

If `ALPHA_VANTAGE_API_KEY` is not set, the app logs a warning and uses **MockMarketDataService** (synthetic data) so you can develop and test without an API key.

## Environment and Alpha Vantage behavior (prod vs non-prod)

Alpha Vantage output size and “limiter” error messages are **config-driven** and determined at **runtime** from the **environment** (production vs non-production):

| | **Non-production** (default) | **Production** |
|--|-------------------------------|----------------|
| **Config** | `meiken.environment = "development"` (or unset) | `meiken.environment = "production"` |
| **Output size** | `compact` — last ~100 trading days (~4 months), free tier | `full` — full history (premium) |
| **Limiter** | **On** — when the requested date range has no data or too few points, errors suggest upgrading to a premium API key | **Off** — generic error messages only |

- **Non-prod:** Uses `outputsize=compact` (free-tier compatible) and turns on the limiter: if the date range is beyond what compact provides (or has insufficient data), the API returns a helpful message suggesting an upgrade for longer history.
- **Prod:** Uses `outputsize=full` (no date window limit) and turns off the limiter; missing or insufficient data returns a generic error.

> **Note — adjusted prices and premium key:** The API uses `TIME_SERIES_DAILY_ADJUSTED` so that close prices are split- and dividend-adjusted (preventing spurious return spikes on corporate action dates). This endpoint works on a free-tier key with `outputsize=compact`. However, `outputsize=full` (production mode, for full price history beyond ~4 months) **requires a premium Alpha Vantage API key**. If a premium key is not available, leave the environment as `development` and requests will be limited to the most recent ~100 trading days.

**How to set:**

- **Config file** (`src/main/resources/application.conf`): set `meiken.environment = "production"` for production.
- **Environment variable:** `MEIKEN_ENVIRONMENT=production` overrides the config (e.g. in production deploy).

Default is `development` (non-production), so local and test runs use compact and the limiter without extra config.

## Run Commands

| Command           | Description                    |
|-------------------|--------------------------------|
| `./gradlew run`   | Start server on port 8080      |
| `./gradlew test`  | Run all unit tests             |
| `./gradlew build` | Compile, test, and build JAR   |

## Data and calculations

- **Prices:** Close-of-day only. One closing price per calendar day per ticker (from Alpha Vantage `TIME_SERIES_DAILY_ADJUSTED` or mock). Adjusted prices correct for splits and dividends, preventing spurious return spikes on corporate action dates.
- **Returns:** Day-over-day percentage change: `(close_t - close_{t-1}) / close_{t-1}`.
- **Volatility, alpha, beta, Sharpe, Sortino, correlation:** All computed from these close-of-day returns (no intraday or open/high/low).
- **Calmar, maximum drawdown:** Computed from the close-of-day price series directly.

### Market data and calendar

- **Trading calendar:** The API uses a built-in **US equity (NYSE/NASDAQ) calendar** for trading-day counts and data-quality checks (e.g. expected vs actual trading days). Holiday dates are computed from **year-dependent rules** (e.g. Easter, Thanksgiving). For production use over long or multi-year ranges, or for **up-to-date holiday lists**, consider integrating a **third-party calendar or holiday data source**.
- **Beyond US markets:** If the tool is expanded to other regions or exchanges, a **third-party integration** is recommended to source **exchange-specific holidays and trading hours**, since rules and observance vary by country and venue.

## Analytics cache and concurrency

The API is built for **high concurrency and availability**. Analytics (returns, alpha, volatility, beta, Sharpe, Sortino, Calmar, drawdown, correlation) share a single **SymbolAnalytics** cache per symbol/date-range:

- **Thread-safe:** Cache hits are **lock-free** (Caffeine is thread-safe); many requests for different keys are served in parallel.
- **Per-key coalescing on miss:** When the cache misses for a key, only **one** request computes (fetches prices and calculates metrics); other concurrent requests for the **same** key wait for that result instead of triggering duplicate API calls (no thundering herd). Requests for **different** keys compute in parallel.
- **Robust:** One API call and one computation per symbol/date-range; subsequent requests are served from cache or from the in-flight computation. Failures are propagated to all waiters for that key.

**Cache expiration and eviction (symbol analytics):**

- **Expiration:** **Expire-after-write (TTL).** Each cache entry is removed after a fixed time since it was written. Default: **1 hour** (`meiken.cache.ttl` = 3600 seconds). Override with env `CACHE_TTL` (seconds).
- **Eviction:** When the cache is full, entries are evicted by size. Default **max size** 1000 entries (`meiken.cache.maxSize`). Override with env `CACHE_MAX_SIZE`.
- **Staleness (observability only):** `staleSeconds` (default 24 h) and `oneHourSeconds` (3600) are used only for the `dataFreshness` label on responses ("realtime" / "1 hour old" / "stale") and for a log warning when serving stale data for requests that include recent dates. They do **not** change when entries are expired; expiration is purely TTL + max size.

This keeps the service performant and rate-limit friendly under heavy concurrent usage.

## Design rationale for additional analytics

Beyond the required **Returns** and **Alpha** endpoints, the following analytics were added and exposed as separate endpoints:

| Addition       | Rationale |
|----------------|-----------|
| **Volatility** | Standard deviation of daily returns (daily and annualized). Core risk metric; users need to assess how much a ticker’s returns vary over the period. |
| **Sharpe ratio** | Risk-adjusted return: (annualized return − risk-free rate) / annualized volatility. Lets users compare returns per unit of risk; configurable risk-free rate. |
| **Beta**       | Sensitivity of target to benchmark: covariance(target, benchmark) / variance(benchmark). Standard measure of systematic (market) risk relative to a benchmark. |
| **Rolling correlation** | Correlation between two tickers over a configurable window. Surfaces how closely two series move together over time; useful for diversification and pair strategies. |
| **Sortino ratio** | Like Sharpe, but divides excess return by downside deviation only (semi-deviation of negative returns). Penalizes only harmful volatility; useful when return distributions are asymmetric. |
| **Calmar ratio** | Annualized return divided by maximum drawdown. Measures return earned per unit of worst-case drawdown; commonly used to evaluate trend-following and managed-futures strategies. |
| **Maximum drawdown** | Largest peak-to-trough decline in the price series, with peak/trough dates and optional recovery date. Standard measure of tail risk and worst-case loss for a period. |

All seven reuse the same **close-of-day prices/returns** and **SymbolAnalytics** cache as Returns and Alpha, so there is no extra market-data cost and behavior stays consistent (precision, date alignment, numerical stability).

## API Endpoints

Base URL: `http://localhost:8080/api/v1`

All date params are optional; if omitted, **year-to-date (YTD)** is used. Date format: `YYYY-MM-DD`. All endpoints use close-of-day prices as described above.

### Health

```bash
curl http://localhost:8080/health
```

### Returns (daily close-to-close returns for a ticker)

```bash
# YTD
curl "http://localhost:8080/api/v1/tickers/AAPL/returns"

# With date range
curl "http://localhost:8080/api/v1/tickers/AAPL/returns?from_date=2024-01-01&to_date=2024-06-30"
```

### Alpha (Jensen's alpha via OLS; close-of-day returns)

```bash
curl "http://localhost:8080/api/v1/alpha?target=AAPL&benchmark=SPY"
curl "http://localhost:8080/api/v1/alpha?target=AAPL&benchmark=SPY&from_date=2024-01-01&to_date=2024-06-30"
```

### Volatility (from close-of-day returns)

```bash
curl "http://localhost:8080/api/v1/tickers/AAPL/volatility"
curl "http://localhost:8080/api/v1/tickers/AAPL/volatility?from_date=2024-01-01&to_date=2024-06-30"
```

### Sharpe Ratio (from close-of-day returns)

```bash
# Default risk-free rate 0.04
curl "http://localhost:8080/api/v1/tickers/AAPL/sharpe"
# Custom risk-free rate
curl "http://localhost:8080/api/v1/tickers/AAPL/sharpe?risk_free_rate=0.02"
```

### Beta (from close-of-day returns)

```bash
curl "http://localhost:8080/api/v1/beta?target=AAPL&benchmark=SPY"
```

### Rolling Correlation (from close-of-day returns)

```bash
# Default 30-day window
curl "http://localhost:8080/api/v1/correlation?ticker1=AAPL&ticker2=SPY"
# Custom window
curl "http://localhost:8080/api/v1/correlation?ticker1=AAPL&ticker2=SPY&window=60&from_date=2024-01-01&to_date=2024-06-30"
```

### Sortino Ratio (from close-of-day returns)

```bash
# Default risk-free rate 0.04
curl "http://localhost:8080/api/v1/tickers/AAPL/sortino"
# Custom risk-free rate
curl "http://localhost:8080/api/v1/tickers/AAPL/sortino?risk_free_rate=0.02"
```

### Calmar Ratio (from close-of-day prices)

```bash
curl "http://localhost:8080/api/v1/tickers/AAPL/calmar"
curl "http://localhost:8080/api/v1/tickers/AAPL/calmar?from_date=2024-01-01&to_date=2024-12-31"
```

### Maximum Drawdown (from close-of-day prices)

```bash
curl "http://localhost:8080/api/v1/tickers/AAPL/drawdown"
curl "http://localhost:8080/api/v1/tickers/AAPL/drawdown?from_date=2024-01-01&to_date=2024-12-31"
```

## Calculation Methodology

### Jensen's Alpha (OLS)

Alpha is computed via OLS single-factor regression of excess returns: `(target − rf) = α + β(benchmark − rf) + ε`. Since the risk-free rate is a constant daily shift it cancels in covariance and variance, so:

```
β = cov(target, benchmark) / var(benchmark)
α_daily = mean(target) − β × mean(benchmark) − rf_daily × (1 − β)
α_annualized = (1 + α_daily)^252 − 1
```

where `rf_daily = (1 + riskFreeRate)^(1/252) − 1`. The response also includes beta and the target/benchmark annualized returns for transparency.

### Geometric Mean Annualization

Return series are annualized using the geometric mean (compound growth rate):

```
annualized = (∏(1 + r_t))^(252/n) − 1
```

This is mathematically correct for compound returns and avoids the ~σ²/2 per year upward bias introduced by arithmetically annualizing the mean daily return (Jensen's inequality).

### Sample Statistics (N-1)

Variance, standard deviation, and covariance all use N-1 (Bessel's correction) as denominator — the unbiased estimators for a sample drawn from a larger population. This is standard for financial returns estimated from a finite time window.

### Outlier Winsorization

Rather than removing outlier returns, the API **winsorizes** them (caps extreme values at ±Nσ, default 3σ). This preserves series length and date alignment across symbols — critical for beta and alpha, which require paired returns on the same dates. Removed points would cause misalignment. Winsorized values are used for all numeric calculations; raw returns are still returned in the response for transparency. The count of winsorized observations appears in `metadata.outlierCount`.

### Plausibility Checks

Every calculated metric is run through soft plausibility checks (never reject — only warn). Warnings are appended to `metadata.warnings` so callers can decide whether to trust the value. Bounds reflect convention for exchange-listed equities and ETFs:

| Metric | Warning range |
|--------|--------------|
| Sharpe / Sortino | outside [−5, 5] |
| Beta | outside [−3, 3] |
| Alpha (annualized OLS) | outside [−1, 1] |
| Annualized volatility | outside [0.05, 3.0] |
| Max drawdown | negative or > 0.99 |
| Calmar | outside [−10, 10] (NaN = undefined; ±∞ = valid) |

## Error Responses

Errors return JSON with `error.code` and `error.message`:

- **400** – Invalid request (e.g. invalid symbol, bad date format, date range &gt; 365 days, missing required params)
- **404** – Symbol not found
- **500** – Data retrieval or internal error

Example:

```json
{"error":{"code":"INVALID_DATE_RANGE","message":"Date range cannot exceed 365 days (requested 400 days)","details":null}}
```

- **401** – Unauthorized (missing or invalid API key when API keys are enabled)
- **429** – Too Many Requests (rate limit exceeded; check `Retry-After` header)

## Security

Security is **configurable**: disabled by default for development, enabled for production via config or environment variables.

### API key authentication

When enabled, **all `/api/v1/**` endpoints** require a valid API key. `/health` and `/metrics` remain open.

- **Enable:** Set `API_KEYS_ENABLED=true` (or `meiken.security.apiKeysEnabled = true` in config).
- **Valid keys:** Set `VALID_API_KEYS` to a comma-separated list of keys (e.g. `VALID_API_KEYS=key1,key2`). Keys are plain text; for production consider hashing and a database (extensible later).

**How to pass the API key:**

- **Header (recommended):** `X-API-Key: your_key_here`
- **Query parameter:** `?api_key=your_key_here` (use only when header is not possible; query params may be logged)

Example:

```bash
curl -H "X-API-Key: your_key" "http://localhost:8080/api/v1/tickers/AAPL/returns"
```

Invalid or missing key returns **401** with JSON `{"error":{"code":"UNAUTHORIZED","message":"Missing or invalid API key. Use X-API-Key header or api_key query parameter."}}`.

### Rate limiting

- **/health** and **/metrics:** No rate limit (for health checks and Prometheus scraping).
- **/api/v1/**: Limited per client (per IP). Default: **100 requests per minute** (configurable via `meiken.rateLimit.requestsPerMinute` or env).

When the limit is exceeded, the server returns **429 Too Many Requests** with a `Retry-After` header (seconds until the bucket refills). Response headers:

- `X-RateLimit-Limit` – max requests per window  
- `X-RateLimit-Remaining` – remaining requests  
- `X-RateLimit-Reset` – timestamp when the limit resets  

### CORS

- **Development:** When `ALLOWED_ORIGINS` is not set, all origins are allowed (`anyHost()`).
- **Production:** Set `ALLOWED_ORIGINS` to a comma-separated list of allowed origins (e.g. `https://app.example.com,https://admin.example.com`).
- **Methods:** Only **GET** is allowed (read-only API).
- **Headers:** `X-API-Key` and `X-Correlation-ID` are allowed.

### TLS / HTTPS

- **Production:** Prefer terminating TLS at a **reverse proxy** (nginx, AWS ALB, etc.). The app runs over HTTP behind the proxy; the proxy handles HTTPS, certificates, and HSTS.
- **Optional in-app TLS:** You can configure SSL in `application.conf` under `ktor.security.ssl` (keyStore, keyStorePassword, keyAlias) and set `SSL_PORT` (default 8443). Use env vars `KEY_STORE_PATH`, `KEY_STORE_PASSWORD`, `KEY_ALIAS` for secrets.
- When `REQUIRE_HTTPS=true` (or `meiken.security.requireHttps = true`), the app adds **Strict-Transport-Security** (HSTS) to responses.

### Security headers

Every response includes:

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 1; mode=block`
- `Content-Security-Policy: default-src 'none'`

### Input validation

- **Ticker symbols:** 1–5 uppercase alphanumeric characters only.
- **Dates:** ISO-8601 (YYYY-MM-DD), not in the future; optional params validated when present.
- **Strings:** Control characters are stripped; max length enforced to reduce injection risk.

### Best practices

- Use **header** `X-API-Key` instead of query param when possible (avoids key in logs).
- Rotate API keys periodically; support multiple valid keys during rotation (config supports comma-separated list).
- In production, set `ALLOWED_ORIGINS`, enable API keys, and use HTTPS (reverse proxy or in-app TLS).
- Monitor `api_key_usage_total` and `api_key_authentication_failures_total` (Prometheus) for auditing.

## Resilience

The API uses **Resilience4j** for circuit breaker, retry, timeouts, and graceful shutdown when calling Alpha Vantage.

### Circuit breaker (Alpha Vantage)

- **Purpose:** Stops calling Alpha Vantage when failure rate or slow-call rate is too high; fails fast when the circuit is OPEN.
- **Config:** `meiken.resilience.circuitBreaker` in `application.conf`:
  - `failureRateThreshold`: 50 (open when &gt;50% of calls fail)
  - `slowCallDurationThreshold`: 5000 ms
  - `minimumNumberOfCalls`: 5 (evaluate only after at least 5 calls)
  - `waitDurationInOpenState`: 60000 ms (then try HALF_OPEN)
  - `permittedNumberOfCallsInHalfOpenState`: 3
- **States:** CLOSED → OPEN (after threshold) → HALF_OPEN (after wait) → CLOSED (if probes succeed).
- **When OPEN:** API returns **503 Service Unavailable** with `SERVICE_UNAVAILABLE` and `retry_after_seconds` / `circuit_breaker_state` in details.
- **Health:** `/health` includes `circuit_breaker` for `alpha_vantage` (state, failure_rate, slow_call_rate). Overall status is **degraded** when the circuit is OPEN.

### Retry (Alpha Vantage)

- **Purpose:** Retries transient failures (network, timeouts, rate limit) with exponential backoff.
- **Config:** `meiken.resilience.retry`:
  - `maxAttempts`: 3
  - `waitDuration`: 100 ms, `multiplier`: 2, `maxWaitDuration`: 1000 ms (100 → 200 → 400 ms).
- **Retried:** IOException, SocketTimeoutException, transient DataRetrievalException (e.g. rate limit).
- **Not retried:** SymbolNotFoundException, 4xx client errors (except 429), validation errors.

### Timeouts

- **Alpha Vantage HTTP client:** `meiken.resilience.timeout.alphaVantageTimeout` (default 30 s), `connectionTimeout` (default 10 s).
- **Connection pool:** Keep-alive 60 s, up to 50 connections per route (CIO).
- **On timeout:** **504 Gateway Timeout** with `GATEWAY_TIMEOUT`.

### Graceful shutdown

- **Shutdown hook:** On SIGTERM/SIGINT, the app sets a shutdown flag and waits up to 30 s before exit.
- **Health during shutdown:** `/health` returns **503** with status "Application is shutting down" so load balancers can remove the instance.
- **Metric:** `graceful_shutdown_duration_seconds` records how long the shutdown wait ran.

### Monitoring circuit breaker

- **Metrics:** `circuit_breaker_state_gauge` (0=CLOSED, 1=OPEN, 2=HALF_OPEN), `circuit_breaker_calls_total` (state, kind).
- **Health:** `GET /health` → `dependencies[].circuit_breaker` for `alpha_vantage` (state, failure_rate, slow_call_rate).
- **When circuit is OPEN:** Reduce traffic to Alpha Vantage, check upstream health and rate limits; after `waitDurationInOpenState` the circuit moves to HALF_OPEN and a few test calls are allowed.

## Performance

### Response compression

- **Ktor Compression** plugin: gzip and deflate enabled when `meiken.performance.compression.enabled` is true (default).
- **Config:** `meiken.performance.compression` in `application.conf`: `minSize` (bytes, default 1024), `level` (1–9, default 6).
- **Content-Encoding:** Set automatically by the plugin when the client sends `Accept-Encoding: gzip` or `deflate`.

### Caching strategy

- **In-memory (default):** Caffeine cache for symbol analytics; single instance only. Cache is **non-critical** (cold start is acceptable). Expiration is **expire-after-write (TTL)** default 1 hour; max size 1000 entries (see [Analytics cache and concurrency](#analytics-cache-and-concurrency)).
- **Horizontal scaling:** For multiple instances, use a **distributed cache** (e.g. Redis). Config placeholder: `meiken.cache.type = "redis"` and `meiken.cache.redis` (commented in `application.conf`). Redis implementation is optional/future.
- **Cache-Control headers:**  
  - `/health`: `no-cache`  
  - `/metrics`: `no-store`  
  - `/api/v1/*`: `public, max-age=300` (5 minutes)

### Parallel processing

- **Alpha endpoint:** Fetches target and benchmark in parallel via `coroutineScope` + `async`; metrics: `parallel_fetch_duration_seconds`, `parallel_operations_total`.
- **Connection pool:** 50 connections per route, keep-alive 60 s (see Resilience section).

### Resource requirements

- **Typical:** 256Mi memory, 100m CPU (requests); limits 512Mi / 500m for bursts.
- **Docker:** `JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200` (see Dockerfile).

## Scaling

### Horizontal scaling

- **Stateless design:** No local session state; only the symbol analytics cache is in-memory (per instance). **Session affinity is not required**; any instance can serve any request.
- **Cache:** Per-instance Caffeine cache; cache is **non-critical** (cold cache only affects latency, not correctness). For shared cache across instances, add Redis (see Performance / Caching strategy).
- **Kubernetes:** Deploy with the manifests in `k8s/` (Deployment, Service, HPA, ConfigMap). Readiness and liveness use `/health`. HPA targets CPU 70% and memory 80% (min 2, max 10 replicas).
- **Load testing:** Use k6: `k6 run load-test/script.js`. Script runs baseline (10 VU), stress (100 VU), and spike (500 VU). Success criteria: p95 &lt; 500 ms, error rate &lt; 1%.

### Kubernetes deployment

```bash
# Build image
docker build -t equity-analytics-api:latest .

# Apply manifests (create secret for ALPHA_VANTAGE_API_KEY if needed)
kubectl apply -f k8s/ConfigMap.yaml
kubectl create secret generic equity-analytics-api-secrets --from-literal=alpha-vantage-api-key=YOUR_KEY  # optional
kubectl apply -f k8s/Deployment.yaml
kubectl apply -f k8s/Service.yaml
kubectl apply -f k8s/HorizontalPodAutoscaler.yaml
```

### Load testing (k6)

```bash
# Install k6: https://k6.io/docs/getting-started/installation/
k6 run load-test/script.js

# Custom base URL
BASE_URL=http://your-host:8080 k6 run load-test/script.js
```

### Monitoring dashboard

- **Grafana:** Example panels and Prometheus config in `grafana/` (`dashboard.json`, `prometheus.yml`). Panels: request rate, latency (p50/p95/p99), error rate, circuit breaker state, cache hit rate, parallel fetch duration.

## Tests

```bash
./gradlew test
```

Reports: `build/reports/tests/test/index.html`  
Coverage: `build/reports/jacoco/test/html/index.html`

## Docker

Build (optionally pass API key at build time):

```bash
docker build -t equity-analytics-api .
# With API key at build time (optional; prefer runtime -e for secrets):
docker build --build-arg ALPHA_VANTAGE_API_KEY=your_key -t equity-analytics-api .
```

Run (port 8080; pass API key at runtime for real data):

```bash
docker run -p 8080:8080 equity-analytics-api
# With real data:
docker run -p 8080:8080 -e ALPHA_VANTAGE_API_KEY=your_key equity-analytics-api
```

## Monitoring and observability

The API exposes production-grade metrics and health for Prometheus and structured logging with correlation IDs.

### Metrics endpoint (Prometheus)

- **URL:** `GET http://localhost:8080/metrics`
- **Content-Type:** `text/plain` (Prometheus exposition format)
- Use for scraping by Prometheus or viewing raw metrics.

**Example Prometheus scrape config** (`prometheus.yml`):

```yaml
scrape_configs:
  - job_name: 'equity-analytics-api'
    metrics_path: /metrics
    static_configs:
      - targets: ['localhost:8080']
```

### Key metrics

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `api_requests_total` | Counter | endpoint, status, method | Total API requests |
| `api_request_duration_seconds` | Timer | endpoint | Request duration |
| `in_flight_requests` | Gauge | — | Requests currently being processed |
| `cache_hits_total` | Counter | — | Symbol analytics cache hits |
| `cache_misses_total` | Counter | — | Symbol analytics cache misses |
| `cache_size` | Gauge | — | Current cache entry count |
| `cache_hit_rate` | Gauge | — | Hit rate (0.0–1.0) |
| `alpha_vantage_calls_total` | Counter | symbol, status | Alpha Vantage API calls (status: success, error, rate_limit, symbol_not_found, no_data, insufficient_data) |
| `alpha_vantage_request_duration_seconds` | Timer | — | Alpha Vantage request duration |
| `api_key_usage_total` | Counter | key_id, endpoint | API key usage (when API key auth is enabled) |
| `api_key_authentication_failures_total` | Counter | — | Failed API key authentication attempts |
| `circuit_breaker_state_gauge` | Gauge | name | Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN) |
| `circuit_breaker_calls_total` | Counter | name, state, kind | Circuit breaker calls (success, error, call_not_permitted) |
| `retry_attempts_total` | Counter | outcome | Retry attempts by outcome |
| `request_timeout_total` | Counter | — | Request timeouts |
| `graceful_shutdown_duration_seconds` | Timer | — | Graceful shutdown wait duration |
| `parallel_fetch_duration_seconds` | Timer | — | Duration of parallel symbol fetches (e.g. alpha) |
| `parallel_operations_total` | Counter | — | Number of parallel operations |
| `response_size_bytes` | DistributionSummary | endpoint | Response body size in bytes |

### Example Grafana / PromQL queries

- Request rate: `rate(api_requests_total[5m])`
- P95 latency by endpoint: `histogram_quantile(0.95, rate(api_request_duration_seconds_bucket[5m])) by (le, endpoint)`
- Cache hit rate: `cache_hit_rate` or `rate(cache_hits_total[5m]) / (rate(cache_hits_total[5m]) + rate(cache_misses_total[5m]))`
- Alpha Vantage errors: `sum(rate(alpha_vantage_calls_total{status!="success"}[5m])) by (status)`

### Health endpoint (enhanced)

- **URL:** `GET http://localhost:8080/health`
- Returns JSON with overall status (`healthy` | `degraded` | `unhealthy`), dependencies (Alpha Vantage connectivity and circuit breaker state, cache status with size and hit rate), and system info (memory, uptime).
- When the Alpha Vantage circuit breaker is OPEN, the `alpha_vantage` dependency includes `circuit_breaker: { state, failure_rate, slow_call_rate }` and overall status is **degraded**.
- During graceful shutdown, the endpoint returns **503** with a dependency indicating "Application is shutting down".

### Correlation ID and logging

- Every request gets a **correlation ID** (generated or from `X-Correlation-ID` request header).
- It is stored in MDC and added to the **response header** `X-Correlation-ID`.
- Logs include the correlation ID when using the pattern format; in JSON format it is in the `correlationId` MDC field.
- Use it to trace a request across logs and services.

### Logging format

- **Default:** JSON (Logstash encoder) with timestamp, level, logger, thread, message, MDC (`correlationId`, `requestId`, `userId`), and exception stack traces.
- Override with your own `logback.xml` or set `MEIKEN_LOG_FORMAT=pattern` if you use a custom config that checks this.

---

## Tech Stack

- **Kotlin** 1.8, **Ktor** 2.3, **Gradle** (Kotlin DSL)
- **kotlinx-datetime**, **kotlinx-serialization**, **kotlinx-coroutines**
- **Ktor Client** (CIO) for Alpha Vantage (connection pool, timeouts)
- **Ktor Compression** for gzip/deflate response compression
- **Resilience4j** for circuit breaker, retry, and resilience metrics
- **Caffeine** for in-memory caching (single instance); Redis optional for horizontal scaling
- **Micrometer** + **Prometheus** for metrics
- **Logback** + **logstash-logback-encoder** for JSON logging
- **JUnit 5** for tests

## Documentation

Additional docs (API spec, architecture, runbooks) are in **docs/**. See **[docs/README.md](docs/README.md)** for an index and which documents are maintained vs historical.

## License and legal

| Document | Description |
|----------|-------------|
| [LICENSE](LICENSE) | Proprietary source code license (view-only) |
| [docs/NOTICE.md](docs/NOTICE.md) | Copyright and portfolio notice |
| [docs/legal/TERMS_OF_SERVICE.md](docs/legal/TERMS_OF_SERVICE.md) | API terms of service |
| [docs/legal/PRIVACY_POLICY.md](docs/legal/PRIVACY_POLICY.md) | Privacy policy |

Legal documents are also served by the running API: `GET /legal`, `/legal/terms`, `/legal/privacy`, `/legal/notice`.

**© 2024–2026 James Carson. All Rights Reserved.** Commercial or API licensing: jamescarson3rd@gmail.com
