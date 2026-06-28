# Equity Analytics API - Financial Analytics REST API Specification

## Overview
Equity Analytics API is a REST API for computing financial analytics on stock market data, including returns, alpha, and other performance metrics.

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
   - Maximum range: 1 year (365 days, configurable)
   - `from_date` must be before or equal to `to_date`
   - Dates cannot be in the future
2. Default behavior: If no dates provided, use Year-to-Date (YTD)
3. Data source: Alpha Vantage API (or abstracted service layer)

**Response Format** (JSON; field names as implemented):
```json
{
  "symbol": "AAPL",
  "fromDate": "2024-01-01",
  "toDate": "2024-12-31",
  "dailyReturns": [
    { "date": "2024-01-02", "returnValue": 0.0234 },
    { "date": "2024-01-03", "returnValue": -0.0156 }
  ],
  "metadata": {
    "dataPoints": 252,
    "source": "market_data",
    "dataQuality": "GOOD",
    "outlierCount": 0,
    "missingDays": 0,
    "warnings": null
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

**Response Format** (JSON; field names as implemented):
```json
{
  "target": "AAPL",
  "benchmark": "SPY",
  "fromDate": "2024-01-01",
  "toDate": "2024-12-31",
  "alpha": 0.0523,
  "metadata": {
    "dataPoints": 252,
    "calculationMethod": "jensens_alpha_ols",
    "riskFreeRate": 0.04,
    "beta": 1.12,
    "targetAnnualizedReturn": 0.2845,
    "benchmarkAnnualizedReturn": 0.2322,
    "dataQuality": "GOOD",
    "outlierCount": 0,
    "missingDays": 0,
    "warnings": null
  }
}
```

**Alpha Calculation** (Jensen's alpha via OLS):
Alpha is computed via OLS single-factor regression: `(target − rf) = α + β(benchmark − rf) + ε`.
```
1. Calculate daily returns for both target and benchmark (close-to-close)
2. β = cov(target, benchmark) / var(benchmark)   [sample covariance/variance, N-1]
3. rf_daily = (1 + riskFreeRate)^(1/252) − 1
4. α_daily = mean(target) − β × mean(benchmark) − rf_daily × (1 − β)
5. α_annualized = (1 + α_daily)^252 − 1   [geometric annualization]
```
Outlier returns are winsorized at ±3σ before calculation to prevent data errors from distorting the regression.

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

**Response** (camelCase as implemented):
```json
{
  "symbol": "AAPL",
  "fromDate": "2024-01-01",
  "toDate": "2024-12-31",
  "volatility": { "daily": 0.0189, "annualized": 0.2987 }
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

#### 3.5 Get Sortino Ratio
**Endpoint**: `GET /api/v1/tickers/{symbol}/sortino`

Like Sharpe ratio, but divides excess return by downside deviation only (semi-deviation of negative returns, annualized). Penalizes only harmful volatility.

**Query Parameters**:
- `risk_free_rate` (optional): Annual risk-free rate (default: 0.04)

**Sortino Calculation**:
```
downside_deviation = sqrt(mean(r_t^2 for r_t < 0)) × sqrt(252)
sortino = (annualized_return - risk_free_rate) / downside_deviation
```

#### 3.6 Get Calmar Ratio
**Endpoint**: `GET /api/v1/tickers/{symbol}/calmar`

Return earned per unit of maximum drawdown risk. `+∞` when drawdown is zero and return is positive (valid).

**Calmar Calculation**:
```
calmar = annualized_return / max_drawdown
```

#### 3.7 Get Maximum Drawdown
**Endpoint**: `GET /api/v1/tickers/{symbol}/drawdown`

Largest peak-to-trough decline in the close-of-day price series. Returns peak date, trough date, their values, and the first recovery date (if any).

**Max Drawdown Calculation**:
```
For each price, track running peak. drawdown_t = (peak - price_t) / peak.
maxDrawdown = max(drawdown_t) over all t.
```

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
    "message": "Date range cannot exceed 365 days (requested 400 days)",
    "details": null
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
(Returns a list of close-of-day prices; no Result wrapper.)

### Implementations
1. **AlphaVantageService**: Production implementation
2. **MockMarketDataService**: For testing and development

---

## Legal Endpoints

Public markdown documents (bundled from `docs/legal/` at build time):

| Endpoint | Document |
|----------|----------|
| `GET /legal` | Index with links |
| `GET /legal/terms` | Terms of Service |
| `GET /legal/privacy` | Privacy Policy |
| `GET /legal/notice` | Copyright and portfolio notice |

---

## Success Criteria

1. ✅ All required endpoints implemented and functional
2. ✅ Proper input validation and error handling
3. ✅ Comprehensive unit test coverage (≥90% instruction coverage)
4. ✅ Integration tests for API endpoints (Ktor test host)
5. ✅ Structured logging throughout (JSON, correlation IDs)
6. ✅ Clear code structure with separation of concerns
7. ✅ README with setup and usage instructions
8. ✅ Numerical accuracy verified with known test cases
9. ✅ Performance meets requirements (target p95 &lt; 500 ms for typical request)
