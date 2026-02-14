# Meiken

A Kotlin/Ktor REST API for financial analytics: daily returns, alpha, volatility, beta, Sharpe ratio, and rolling correlation. Uses Alpha Vantage for real market data (or mock data when no API key is set).

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

## Run Commands

| Command           | Description                    |
|-------------------|--------------------------------|
| `./gradlew run`   | Start server on port 8080      |
| `./gradlew test`  | Run all unit tests             |
| `./gradlew build` | Compile, test, and build JAR   |

## API Endpoints

Base URL: `http://localhost:8080/api/v1`

All date params are optional; if omitted, **year-to-date (YTD)** is used. Date format: `YYYY-MM-DD`.

### Health

```bash
curl http://localhost:8080/health
```

### Returns (daily returns for a ticker)

```bash
# YTD
curl "http://localhost:8080/api/v1/tickers/AAPL/returns"

# With date range
curl "http://localhost:8080/api/v1/tickers/AAPL/returns?from_date=2024-01-01&to_date=2024-06-30"
```

### Alpha (excess return vs benchmark)

```bash
curl "http://localhost:8080/api/v1/alpha?target=AAPL&benchmark=SPY"
curl "http://localhost:8080/api/v1/alpha?target=AAPL&benchmark=SPY&from_date=2024-01-01&to_date=2024-06-30"
```

### Volatility

```bash
curl "http://localhost:8080/api/v1/tickers/AAPL/volatility"
curl "http://localhost:8080/api/v1/tickers/AAPL/volatility?from_date=2024-01-01&to_date=2024-06-30"
```

### Sharpe Ratio

```bash
# Default risk-free rate 0.04
curl "http://localhost:8080/api/v1/tickers/AAPL/sharpe"
# Custom risk-free rate
curl "http://localhost:8080/api/v1/tickers/AAPL/sharpe?risk_free_rate=0.02"
```

### Beta

```bash
curl "http://localhost:8080/api/v1/beta?target=AAPL&benchmark=SPY"
```

### Rolling Correlation

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
