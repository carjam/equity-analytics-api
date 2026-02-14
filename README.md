# Meiken

A Kotlin/Ktor REST API for financial analytics: daily returns, alpha, volatility, beta, Sharpe ratio, and rolling correlation. Uses Alpha Vantage for real market data (or mock data when no API key is set).

**All calculations use close-of-day (close-of-date) prices only:** one price per calendar day per ticker (the daily closing price). Returns are day-over-day close-to-close; volatility, alpha, beta, Sharpe, and correlation are derived from those daily close-based returns.

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

- **Prices:** Close-of-day only. One closing price per calendar day per ticker (from Alpha Vantage TIME_SERIES_DAILY or mock).
- **Returns:** Day-over-day percentage change: `(close_t - close_{t-1}) / close_{t-1}`.
- **Volatility, alpha, beta, Sharpe, correlation:** All computed from these close-of-day returns (no intraday or open/high/low).

## Analytics cache and concurrency

The API is built for **high concurrency and availability**. Analytics (returns, alpha, volatility, beta, Sharpe, correlation) share a single **SymbolAnalytics** cache per symbol/date-range:

- **Thread-safe:** Cache hits are **lock-free** (Caffeine is thread-safe); many requests for different keys are served in parallel.
- **Per-key coalescing on miss:** When the cache misses for a key, only **one** request computes (fetches prices and calculates metrics); other concurrent requests for the **same** key wait for that result instead of triggering duplicate API calls (no thundering herd). Requests for **different** keys compute in parallel.
- **Robust:** One API call and one computation per symbol/date-range; subsequent requests are served from cache or from the in-flight computation. Failures are propagated to all waiters for that key.

This keeps the service performant and rate-limit friendly under heavy concurrent usage.

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

### Alpha (excess return vs benchmark; close-of-day returns)

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

## Error Responses

Errors return JSON with `error.code` and `error.message`:

- **400** – Invalid request (e.g. invalid symbol, bad date format, date range &gt; 365 days, missing required params)
- **404** – Symbol not found
- **500** – Data retrieval or internal error

Example:

```json
{"error":{"code":"INVALID_DATE_RANGE","message":"Date range cannot exceed 365 days (requested 400 days)","details":null}}
```

## Tests

```bash
./gradlew test
```

Reports: `build/reports/tests/test/index.html`  
Coverage: `build/reports/jacoco/test/html/index.html`

## Docker

Build (optionally pass API key at build time):

```bash
docker build -t meiken .
# With API key at build time (optional; prefer runtime -e for secrets):
docker build --build-arg ALPHA_VANTAGE_API_KEY=your_key -t meiken .
```

Run (port 8080; pass API key at runtime for real data):

```bash
docker run -p 8080:8080 meiken
# With real data:
docker run -p 8080:8080 -e ALPHA_VANTAGE_API_KEY=your_key meiken
```

## Tech Stack

- **Kotlin** 1.8, **Ktor** 2.3, **Gradle** (Kotlin DSL)
- **kotlinx-datetime**, **kotlinx-serialization**, **kotlinx-coroutines**
- **Ktor Client** (CIO) for Alpha Vantage
- **Caffeine** for caching (when using real API)
- **Logback** for logging
- **JUnit 5** for tests

## License

See repository license.
