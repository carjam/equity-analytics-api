# Equity Analytics API - Architecture Design

## Technology Stack

### Core Framework
- **Language**: Kotlin 1.8
- **Framework**: Ktor (lightweight, async REST framework)
- **Build Tool**: Gradle (Kotlin DSL)
- **JVM**: Java 11 (jvmToolchain(11))

### Libraries
- **HTTP Client**: Ktor Client (CIO, for Alpha Vantage API calls)
- **JSON Serialization**: kotlinx.serialization
- **Logging**: SLF4J + Logback (logstash-logback-encoder for JSON)
- **Testing**: JUnit 5, Ktor Test Host, kotlin-test; ktor-client-mock for data layer
- **Date/Time**: kotlinx-datetime
- **Coroutines**: kotlinx.coroutines
- **Cache**: Caffeine (in-memory symbol analytics)
- **Resilience**: Resilience4j (circuit breaker, retry)
- **Metrics**: Micrometer + Prometheus

---

## Architecture Pattern

### Layered Architecture

```
┌─────────────────────────────────────┐
│         REST API Layer              │
│  (Controllers/Routes - Ktor)        │
└─────────────┬───────────────────────┘
              │
┌─────────────▼───────────────────────┐
│         Service Layer               │
│  (Business Logic & Calculations)    │
└─────────────┬───────────────────────┘
              │
┌─────────────▼───────────────────────┐
│      Data Access Layer              │
│  (Market Data Service Abstraction)  │
└─────────────┬───────────────────────┘
              │
┌─────────────▼───────────────────────┐
│      External APIs / Cache          │
│    (Alpha Vantage, Redis)           │
└─────────────────────────────────────┘
```

---

## Component Design

### 1. REST API Layer (`api` package)

**Responsibilities**:
- Route definition and HTTP handling
- Request validation and parsing
- Response formatting
- Error handling and HTTP status codes

**Key Classes**:
```kotlin
// Route definitions (query/path params; no separate request DTOs)
fun Application.configureRouting(...)
fun Route.returnsRoutes(returnsService, maxStringLength)
fun Route.alphaRoutes(alphaService, maxStringLength)
fun Route.analyticsRoutes(analyticsService, defaultsConfig, maxStringLength)

// Response models (serialized to JSON)
data class Returns(symbol, fromDate, toDate, dailyReturns, metadata)
data class Alpha(target, benchmark, fromDate, toDate, alpha, metadata)
// Plus VolatilityResponse, SharpeResponse, BetaResponse, CorrelationResponse
```

### 2. Service Layer (`service` package)

**Responsibilities**:
- Business logic implementation
- Financial calculations
- Data validation
- Orchestration of data access

**Key Classes**:
```kotlin
interface ReturnsService {
    suspend fun calculateReturns(
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): Returns
}

interface AlphaService {
    suspend fun calculateAlpha(
        target: String,
        benchmark: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): Alpha
}

interface AnalyticsService {
    suspend fun calculateVolatility(symbol, fromDate, toDate): VolatilityResponse
    suspend fun calculateSharpe(symbol, fromDate, toDate, riskFreeRate): SharpeResponse
    suspend fun calculateBeta(target, benchmark, fromDate, toDate): BetaResponse
    suspend fun calculateCorrelation(ticker1, ticker2, fromDate, toDate, window): CorrelationResponse
}
```

### 3. Calculation Engine (`calculator` package)

**Responsibilities**:
- Pure financial calculations
- Numerical algorithms
- No I/O or side effects

**Key Classes**:
```kotlin
object FinancialCalculations {
    fun calculateDailyReturns(prices: List<DailyPrice>): List<DailyReturn>
    fun calculateAlpha(targetReturns: List<Double>, benchmarkReturns: List<Double>, tradingDays: Int = 252): Double
    fun calculateBeta(targetReturns: List<Double>, benchmarkReturns: List<Double>): Double
    fun calculateVolatility(returns: List<Double>, tradingDays: Int = 252): Pair<Double, Double>  // daily, annualized
    fun calculateSharpe(returns: List<Double>, riskFreeRate: Double = 0.04, tradingDays: Int = 252): Double
    
    fun annualizeReturn(avgDailyReturn: Double, tradingDays: Int = 252): Double
}
object StatisticalCalculations {
    fun mean(values: List<Double>): Double
    fun variance(values: List<Double>): Double
    fun standardDeviation(values: List<Double>): Double
    fun covariance(series1: List<Double>, series2: List<Double>): Double
    fun correlation(series1: List<Double>, series2: List<Double>): Double
}
```
(Rolling correlation is implemented in AnalyticsServiceImpl using aligned returns and a window.)

object StatisticalCalculations {
    fun mean(values: List<Double>): Double
    fun variance(values: List<Double>): Double
    fun standardDeviation(values: List<Double>): Double
    fun covariance(series1: List<Double>, series2: List<Double>): Double
    fun correlation(series1: List<Double>, series2: List<Double>): Double
}
```

### 4. Data Access Layer (`data` package)

**Responsibilities**:
- Abstract market data retrieval
- Cache management
- External API integration

**Key Interfaces**:
```kotlin
interface MarketDataService {
    suspend fun getHistoricalPrices(
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<DailyPrice>
}

// Implementations
class AlphaVantageService(client, apiKey, baseUrl, ...) : MarketDataService
class MockMarketDataService : MarketDataService
```
(Symbol analytics are cached in SymbolAnalyticsCacheService (Caffeine), not in the data layer. Optional CachingMarketDataService wraps a MarketDataService with a price cache.)

### 5. Domain Models (`model` package)

**Key Data Classes**:
```kotlin
data class DailyPrice(
    val date: LocalDate,
    val close: Double,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val volume: Long? = null
)

data class DailyReturn(
    val date: LocalDate,
    val returnValue: Double
)

data class Returns(
    val symbol: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val dailyReturns: List<DailyReturn>,
    val metadata: ReturnsMetadata
)

data class Alpha(
    val target: String,
    val benchmark: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val alpha: Double,
    val metadata: AlphaMetadata
)
```

### 6. Error Handling (`error` package)

**Custom Exceptions** (all extend RuntimeException; mapped in StatusPages):
- `InvalidDateRangeException`, `BadRequestException` → 400
- `SymbolNotFoundException` → 404
- `DataRetrievalException`, `RetryExhaustedException` → 500 / 502
- `ExternalServiceException` → 502
- `CircuitBreakerOpenException` → 503
- `TimeoutException` → 504
- `UnauthorizedException` → 401
- `RateLimitExceededException` → 429

**Response**:
- `ErrorResponse(error: ErrorDetail)` with `code`, `message`, `details` (optional map)

---

## Project Structure (high-level)

```
meiken/
├── docs/           # SPEC.md, ARCHITECTURE.md, runbooks, BUILD.md, etc.
├── src/main/kotlin/com/meiken/
│   ├── Application.kt
│   ├── api/        # Routes, ReturnsRoutes, AlphaRoutes, AnalyticsRoutes, Health
│   ├── cache/      # SymbolAnalyticsCacheService
│   ├── config/     # CacheConfig, ApiConfig, DateRangesConfig, ResilienceConfig, ...
│   ├── calculator/ # FinancialCalculations, StatisticalCalculations
│   ├── data/       # MarketDataService, AlphaVantageService, MockMarketDataService, Resilient*, Caching*
│   ├── error/      # Exceptions, ErrorResponse
│   ├── model/      # DailyPrice, DailyReturn, Returns, Alpha, *Response, *Metadata
│   ├── observability/ # Metrics, ObservabilityPlugin
│   ├── resilience/ # CircuitBreakerConfig, RetryConfig
│   ├── security/   # InputValidator, SecurityConfig, ApiKeyAuth, RateLimiting, SecurityHeaders, TlsConfig
│   ├── service/    # ReturnsService, AlphaService, AnalyticsService + Impls
│   ├── util/       # DateUtils, MarketCalendar
│   ├── validator/  # DataValidator
│   └── lifecycle/  # ShutdownState
├── src/main/resources/
│   └── application.conf
├── src/test/       # Unit and integration tests (config, api, service, data, calculator, ...)
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew / gradlew.bat
└── README.md
```

---

## Configuration Management

### application.conf
```hocon
ktor {
    deployment {
        port = 8080
        port = ${?PORT}
    }
    application {
        modules = [ com.meiken.ApplicationKt.module ]
    }
}

meiken {
    alphaVantage {
        apiKey = ${ALPHA_VANTAGE_API_KEY}
        baseUrl = "https://www.alphavantage.co/query"
        timeout = 30000
    }
    
    cache {
        ttl = 3600 // 1 hour in seconds
        maxSize = 1000
    }
    
    rateLimit {
        requestsPerMinute = 100
    }
    
    dateRanges {
        maxDays = 365
    }
}
```

---

## Data Flow

### Example: Get Returns Request

```
1. HTTP Request arrives at Ktor
   GET /api/v1/tickers/AAPL/returns?from_date=2024-01-01&to_date=2024-06-30

2. Route handler validates and parses request
   - Validates ticker format
   - Parses and validates dates
   - Checks date range <= 365 days

3. Calls ReturnsService
   service.calculateReturns("AAPL", fromDate, toDate)

4. ReturnsService orchestrates:
   a. Call MarketDataService to get historical prices
   b. Cache check first, API call if miss
   c. Call FinancialCalculations.calculateDailyReturns()
   d. Build Returns domain object with metadata

5. Route handler converts to ReturnsResponse DTO
   - Serializes to JSON
   - Returns HTTP 200 with body

Error flows:
- Validation error → HTTP 400 with ErrorResponse
- Symbol not found → HTTP 404 with ErrorResponse
- Data retrieval failure → HTTP 500 with ErrorResponse
```

---

## Caching Strategy

### Cache Key Format
```
market_data:{symbol}:{from_date}:{to_date}
```

### Cache Behavior
- TTL: 1 hour (configurable)
- Eviction: LRU (Least Recently Used)
- Size limit: 1000 entries (configurable)

### Implementation
Use Caffeine cache or similar in-memory cache.

---

## Alpha Vantage Integration

### API Endpoints Used
- **TIME_SERIES_DAILY_ADJUSTED**: Get daily historical prices

### Rate Limits
- Free tier: 25 requests per day, 5 per minute
- Premium tier: Higher limits

### Handling Rate Limits
1. Implement exponential backoff
2. Queue requests if needed
3. Cache aggressively
4. Return clear error messages to users

### Response Parsing
```kotlin
{
  "Meta Data": {...},
  "Time Series (Daily)": {
    "2024-01-02": {
      "1. open": "185.64",
      "2. high": "186.19",
      "3. low": "184.27",
      "4. close": "185.64",
      "5. adjusted close": "184.35",
      "6. volume": "58414460"
    }
  }
}
```

---

## Testing Strategy

### Unit Tests
- Test all calculation functions with known inputs/outputs
- Test edge cases: zero returns, negative returns, single data point
- Test statistical accuracy against reference implementations

### Integration Tests
- Test full API endpoints with mock data service
- Test error handling paths
- Test validation logic

### Performance Tests
- Load testing with various data set sizes
- Ensure <500ms response time for typical requests
- Test with 1 year of daily data (~252 data points)

### Test Data
Create realistic test datasets:
- Normal market conditions
- High volatility periods
- Bear market, bull market
- Missing data / gaps

---

## Deployment Considerations

### Docker
```dockerfile
FROM openjdk:17-alpine
COPY build/libs/equity-analytics-api-all.jar /app/app.jar
EXPOSE 8080
CMD ["java", "-jar", "/app/app.jar"]
```

### Environment Variables
- `ALPHA_VANTAGE_API_KEY`: API key for Alpha Vantage
- `PORT`: HTTP port (default: 8080)
- `LOG_LEVEL`: Logging level (default: INFO)

### Health Check
Implement `/health` endpoint for container orchestration.

---

## Future Enhancements

1. **Database Integration**: Store historical data locally
2. **WebSocket Support**: Real-time price updates
3. **Authentication**: API keys or OAuth
4. **Advanced Analytics**: Machine learning models
5. **Multi-currency Support**: FX conversion
6. **Batch Processing**: Multiple symbols in one request
7. **GraphQL API**: Alternative to REST
8. **Rate Limiting**: Per-user quotas
