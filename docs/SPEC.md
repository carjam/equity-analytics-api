# Meiken - Financial Analytics REST API Specification

## Overview
Meiken is a REST API for computing financial analytics on stock market data, including returns, alpha, and other performance metrics.

## Core Requirements

### 1. Get Returns Endpoint

**Endpoint**: `GET /api/v1/tickers/{symbol}/returns`

**Path Parameters**:
- `symbol` (required): Stock ticker symbol (e.g., AAPL, GOOGL)

**Query Parameters**:
- `from_date` (optional): Start date in ISO-8601 format (YYYY-MM-DD)
- `to_date` (optional): End date in ISO-8601 format (YYYY-MM-DD)

**Business Rules**:
1. Date range validation:
   - Maximum range: 1 year (365 days)
   - `from_date` must be before `to_date`
   - Dates cannot be in the future
2. Default behavior: If no dates provided, use Year-to-Date (YTD)
3. Data source: Alpha Vantage API (or abstracted service layer)

**Response Format**:
```json
{
  "symbol": "AAPL",
  "from_date": "2024-01-01",
  "to_date": "2024-12-31",
  "returns": [
    {
      "date": "2024-01-02",
      "daily_return": 0.0234
    },
    {
      "date": "2024-01-03",
      "daily_return": -0.0156
    }
  ],
  "metadata": {
    "data_points": 252,
    "source": "alpha_vantage"
  }
}
```

**Error Responses**:
- `400 Bad Request`: Invalid ticker, invalid date format, or date range > 1 year
- `404 Not Found`: Ticker symbol not found
- `500 Internal Server Error`: Service unavailable or data retrieval failure

**Daily Return Calculation**:
```
daily_return = (price_today - price_yesterday) / price_yesterday
```

---

### 2. Get Alpha Endpoint

**Endpoint**: `GET /api/v1/alpha`

**Query Parameters**:
- `target` (required): Target ticker symbol
- `benchmark` (required): Benchmark ticker symbol (e.g., SPY for S&P 500)
- `from_date` (optional): Start date in ISO-8601 format
- `to_date` (optional): End date in ISO-8601 format

**Business Rules**:
1. Same date validation as Returns endpoint
2. Both tickers must have overlapping data for the specified period
3. Alpha calculation requires at least 30 trading days of data

**Response Format**:
```json
{
  "target": "AAPL",
  "benchmark": "SPY",
  "from_date": "2024-01-01",
  "to_date": "2024-12-31",
  "alpha": 0.0523,
  "metadata": {
    "data_points": 252,
    "calculation_method": "simple_regression",
    "target_annualized_return": 0.2845,
    "benchmark_annualized_return": 0.2322
  }
}
```

**Alpha Calculation**:
Alpha represents the excess return of the target relative to the benchmark:
```
1. Calculate daily returns for both target and benchmark
2. Compute average daily returns
3. Annualize returns: annualized = (1 + avg_daily_return)^252 - 1
4. Alpha = annualized_return_target - annualized_return_benchmark
```

**Error Responses**:
- `400 Bad Request`: Missing parameters, invalid dates, insufficient data
- `404 Not Found`: One or both tickers not found
- `500 Internal Server Error`: Calculation error or service failure

---

### 3. Additional Analytics Endpoints (Optional Enhancements)

#### 3.1 Get Beta
**Endpoint**: `GET /api/v1/beta`

Calculates beta (systematic risk) of target relative to benchmark.

**Beta Calculation**:
```
beta = covariance(target_returns, benchmark_returns) / variance(benchmark_returns)
```

#### 3.2 Get Volatility
**Endpoint**: `GET /api/v1/tickers/{symbol}/volatility`

Calculates historical volatility (standard deviation of returns).

**Response**:
```json
{
  "symbol": "AAPL",
  "from_date": "2024-01-01",
  "to_date": "2024-12-31",
  "volatility": {
    "daily": 0.0189,
    "annualized": 0.2987
  }
}
```

#### 3.3 Get Sharpe Ratio
**Endpoint**: `GET /api/v1/tickers/{symbol}/sharpe`

Calculates risk-adjusted return.

**Query Parameters**:
- `risk_free_rate` (optional): Annual risk-free rate (default: 0.04)

**Sharpe Calculation**:
```
sharpe = (annualized_return - risk_free_rate) / annualized_volatility
```

#### 3.4 Get Rolling Correlation
**Endpoint**: `GET /api/v1/correlation`

Calculates rolling correlation between two tickers.

**Query Parameters**:
- `ticker1`, `ticker2`: The two tickers to compare
- `window` (optional): Rolling window in days (default: 30)

---

## Cross-Cutting Concerns

### API Versioning
- All endpoints prefixed with `/api/v1/`
- Version in URL path for clarity and stability
- Future versions can coexist: `/api/v2/`

### Input Validation
- Ticker symbols: 1-5 uppercase alphanumeric characters
- Dates: ISO-8601 format (YYYY-MM-DD)
- Date ranges: Maximum 1 year
- Numeric parameters: Validate ranges and formats

### Error Handling
- Consistent error response format:
```json
{
  "error": {
    "code": "INVALID_DATE_RANGE",
    "message": "Date range exceeds maximum of 1 year",
    "details": {
      "max_days": 365,
      "requested_days": 400
    }
  }
}
```

### Logging
- Log all API requests with timestamps
- Log data source calls and latencies
- Log calculation errors with context
- Use structured logging (JSON format)

### Security
- Rate limiting: 100 requests per minute per IP
- Input sanitization to prevent injection
- HTTPS only in production
- API key authentication for Alpha Vantage
- Consider API key auth for public endpoints (future)

### Performance Considerations
- Cache frequently requested ticker data (TTL: 1 hour)
- Optimize calculations for large datasets
- Consider pagination for large result sets
- Async processing for expensive calculations

### Testing Requirements
- Unit tests for all calculation logic
- Integration tests for API endpoints
- Mock external data sources in tests
- Test edge cases: weekends, holidays, missing data
- Load testing for performance validation

---

## Data Source Abstraction

### Interface
```kotlin
interface MarketDataService {
    suspend fun getHistoricalPrices(
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<DailyPrice>
}
```

### Implementations
1. **AlphaVantageService**: Production implementation
2. **MockMarketDataService**: For testing and development

---

## Success Criteria

1. ✅ All required endpoints implemented and functional
2. ✅ Proper input validation and error handling
3. ✅ Comprehensive unit test coverage (>80%)
4. ✅ Integration tests for all endpoints
5. ✅ Structured logging throughout
6. ✅ API documentation (OpenAPI/Swagger)
7. ✅ Clear code structure with separation of concerns
8. ✅ README with setup and usage instructions
9. ✅ Numerical accuracy verified with known test cases
10. ✅ Performance meets requirements (<500ms for typical request)
