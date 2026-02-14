# Meiken - Implementation Guide

## Implementation Phases

### Phase 1: Project Setup ✓
- [x] Create GitHub repository
- [x] Write SPEC.md
- [x] Write ARCHITECTURE.md
- [ ] Initialize Gradle project
- [ ] Configure dependencies
- [ ] Setup project structure

### Phase 2: Core Infrastructure
- [ ] Setup Ktor application
- [ ] Configure routing
- [ ] Implement error handling
- [ ] Setup logging
- [ ] Create domain models

### Phase 3: Data Layer
- [ ] Create MarketDataService interface
- [ ] Implement MockMarketDataService (for testing)
- [ ] Implement AlphaVantageService
- [ ] Add caching layer
- [ ] Write data layer tests

### Phase 4: Calculation Engine
- [ ] Implement FinancialCalculations
- [ ] Implement StatisticalCalculations
- [ ] Add comprehensive unit tests
- [ ] Verify numerical accuracy

### Phase 5: Service Layer
- [ ] Implement ReturnsService
- [ ] Implement AlphaService
- [ ] Add input validation
- [ ] Write service tests

### Phase 6: API Layer
- [ ] Implement GET /returns endpoint
- [ ] Implement GET /alpha endpoint
- [ ] Add request/response validation
- [ ] Write API integration tests

### Phase 7: Additional Analytics (Optional)
- [ ] Implement volatility endpoint
- [ ] Implement beta endpoint
- [ ] Implement Sharpe ratio endpoint
- [ ] Implement correlation endpoint

### Phase 8: Polish & Documentation
- [ ] Add OpenAPI/Swagger documentation
- [ ] Write comprehensive README
- [ ] Add usage examples
- [ ] Performance optimization
- [ ] Code review and refactoring

---

## Development Workflow

### 1. TDD Approach
For each component:
1. Write test cases first (based on spec)
2. Implement minimal code to pass tests
3. Refactor for clarity and performance
4. Add edge case tests

### 2. Git Workflow
```bash
# Feature branch workflow
git checkout -b feature/returns-endpoint
# Make changes, commit frequently
git add .
git commit -m "feat: implement returns calculation"
# Push and create PR
git push origin feature/returns-endpoint
```

### 3. Commit Message Convention
```
feat: add new feature
fix: bug fix
docs: documentation changes
test: add tests
refactor: code refactoring
chore: build/config changes
```

---

## Key Implementation Details

### 1. Date Handling

```kotlin
// Use kotlinx-datetime for consistency
import kotlinx.datetime.*

fun getCurrentYearStart(): LocalDate {
    val now = Clock.System.now()
    val nowLocal = now.toLocalDateTime(TimeZone.currentSystemDefault())
    return LocalDate(nowLocal.year, 1, 1)
}

fun validateDateRange(from: LocalDate, to: LocalDate): Boolean {
    if (from >= to) return false
    val daysBetween = from.daysUntil(to)
    return daysBetween <= 365
}
```

### 2. Returns Calculation

```kotlin
fun calculateDailyReturns(prices: List<DailyPrice>): List<DailyReturn> {
    require(prices.size >= 2) { "Need at least 2 prices to calculate returns" }
    
    return prices
        .sortedBy { it.date }
        .zipWithNext { prev, curr ->
            val returnValue = (curr.close - prev.close) / prev.close
            DailyReturn(curr.date, returnValue)
        }
}
```

### 3. Alpha Calculation

```kotlin
fun calculateAlpha(
    targetReturns: List<Double>,
    benchmarkReturns: List<Double>,
    tradingDays: Int = 252
): Double {
    require(targetReturns.size == benchmarkReturns.size) { 
        "Target and benchmark must have same number of returns" 
    }
    require(targetReturns.isNotEmpty()) { 
        "Returns list cannot be empty" 
    }
    
    val targetAvg = targetReturns.average()
    val benchmarkAvg = benchmarkReturns.average()
    
    val targetAnnualized = annualizeReturn(targetAvg, tradingDays)
    val benchmarkAnnualized = annualizeReturn(benchmarkAvg, tradingDays)
    
    return targetAnnualized - benchmarkAnnualized
}

fun annualizeReturn(avgDailyReturn: Double, tradingDays: Int = 252): Double {
    return Math.pow(1 + avgDailyReturn, tradingDays.toDouble()) - 1
}
```

### 4. Beta Calculation

```kotlin
fun calculateBeta(
    targetReturns: List<Double>,
    benchmarkReturns: List<Double>
): Double {
    val cov = covariance(targetReturns, benchmarkReturns)
    val benchmarkVar = variance(benchmarkReturns)
    
    require(benchmarkVar != 0.0) { "Benchmark variance cannot be zero" }
    
    return cov / benchmarkVar
}
```

### 5. Volatility Calculation

```kotlin
fun calculateVolatility(returns: List<Double>): Pair<Double, Double> {
    val dailyVolatility = standardDeviation(returns)
    val annualizedVolatility = annualizeVolatility(dailyVolatility)
    
    return Pair(dailyVolatility, annualizedVolatility)
}

fun annualizeVolatility(dailyVolatility: Double, tradingDays: Int = 252): Double {
    return dailyVolatility * Math.sqrt(tradingDays.toDouble())
}
```

### 6. Sharpe Ratio Calculation

```kotlin
fun calculateSharpe(
    returns: List<Double>,
    riskFreeRate: Double = 0.04,
    tradingDays: Int = 252
): Double {
    val avgReturn = returns.average()
    val annualizedReturn = annualizeReturn(avgReturn, tradingDays)
    
    val dailyVolatility = standardDeviation(returns)
    val annualizedVolatility = annualizeVolatility(dailyVolatility, tradingDays)
    
    require(annualizedVolatility != 0.0) { "Volatility cannot be zero" }
    
    return (annualizedReturn - riskFreeRate) / annualizedVolatility
}
```

### 7. Error Handling Pattern

```kotlin
// In route handler
get("/api/v1/tickers/{symbol}/returns") {
    try {
        val symbol = call.parameters["symbol"]?.uppercase() 
            ?: throw InvalidInputException("Symbol is required")
        
        val fromDate = call.request.queryParameters["from_date"]?.let {
            LocalDate.parse(it)
        } ?: getCurrentYearStart()
        
        val toDate = call.request.queryParameters["to_date"]?.let {
            LocalDate.parse(it)
        } ?: Clock.System.todayIn(TimeZone.currentSystemDefault())
        
        validateDateRange(fromDate, toDate)
        
        val returns = returnsService.calculateReturns(symbol, fromDate, toDate)
        
        call.respond(HttpStatusCode.OK, returns)
        
    } catch (e: InvalidDateRangeException) {
        call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse(
                error = ErrorDetail(
                    code = "INVALID_DATE_RANGE",
                    message = e.message ?: "Invalid date range",
                    details = mapOf("max_days" to 365)
                )
            )
        )
    } catch (e: SymbolNotFoundException) {
        call.respond(HttpStatusCode.NotFound, ErrorResponse(...))
    } catch (e: Exception) {
        logger.error("Unexpected error", e)
        call.respond(HttpStatusCode.InternalServerError, ErrorResponse(...))
    }
}
```

---

## Testing Examples

### Unit Test Example

```kotlin
class FinancialCalculationsTest {
    
    @Test
    fun `calculateDailyReturns with simple price series`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), 100.0),
            DailyPrice(LocalDate(2024, 1, 2), 105.0),
            DailyPrice(LocalDate(2024, 1, 3), 103.95)
        )
        
        val returns = FinancialCalculations.calculateDailyReturns(prices)
        
        assertEquals(2, returns.size)
        assertEquals(0.05, returns[0].returnValue, 0.0001)  // 5% gain
        assertEquals(-0.01, returns[1].returnValue, 0.0001) // 1% loss
    }
    
    @Test
    fun `calculateAlpha with known values`() {
        val targetReturns = List(252) { 0.001 }  // 0.1% daily
        val benchmarkReturns = List(252) { 0.0008 }  // 0.08% daily
        
        val alpha = FinancialCalculations.calculateAlpha(
            targetReturns, 
            benchmarkReturns
        )
        
        // Expected alpha ≈ 0.0514 (5.14% annualized excess return)
        assertEquals(0.0514, alpha, 0.01)
    }
    
    @Test
    fun `calculateDailyReturns throws on insufficient data`() {
        val prices = listOf(DailyPrice(LocalDate(2024, 1, 1), 100.0))
        
        assertThrows<IllegalArgumentException> {
            FinancialCalculations.calculateDailyReturns(prices)
        }
    }
}
```

### Integration Test Example

```kotlin
class ReturnsRoutesTest {
    
    @Test
    fun `GET returns endpoint with valid params`() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        
        val response = client.get(
            "/api/v1/tickers/AAPL/returns?from_date=2024-01-01&to_date=2024-06-30"
        )
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val body = response.body<ReturnsResponse>()
        assertEquals("AAPL", body.symbol)
        assertTrue(body.returns.isNotEmpty())
    }
    
    @Test
    fun `GET returns endpoint rejects excessive date range`() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        
        val response = client.get(
            "/api/v1/tickers/AAPL/returns?from_date=2023-01-01&to_date=2024-06-30"
        )
        
        assertEquals(HttpStatusCode.BadRequest, response.status)
        
        val body = response.body<ErrorResponse>()
        assertEquals("INVALID_DATE_RANGE", body.error.code)
    }
}
```

---

## Build Configuration

### build.gradle.kts

```kotlin
plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    application
}

group = "com.meiken"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:2.3.7")
    implementation("io.ktor:ktor-server-netty:2.3.7")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-server-status-pages:2.3.7")
    
    // Ktor Client (for Alpha Vantage)
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-client-logging:2.3.7")
    
    // Kotlinx
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    
    // Cache
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    
    // Testing
    testImplementation("io.ktor:ktor-server-test-host:2.3.7")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.22")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
}

application {
    mainClass.set("com.meiken.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
```

---

## Performance Optimization Tips

1. **Lazy Computation**: Only calculate what's requested
2. **Parallel Processing**: Use coroutines for concurrent API calls
3. **Efficient Data Structures**: Use arrays for numerical calculations
4. **Cache Aggressively**: Reduce API calls to Alpha Vantage
5. **Database**: Consider storing frequently accessed data locally

---

## Debugging Tips

1. **Enable Debug Logging**:
```kotlin
// In logback.xml
<logger name="com.meiken" level="DEBUG"/>
```

2. **Log API Calls**:
```kotlin
logger.debug("Fetching data for $symbol from $fromDate to $toDate")
```

3. **Log Calculation Inputs**:
```kotlin
logger.debug("Calculating returns for ${prices.size} prices")
```

4. **Use Ktor's Call Logging**:
```kotlin
install(CallLogging) {
    level = Level.INFO
}
```

---

## Pre-Interview Checklist

- [ ] All endpoints working and tested
- [ ] Code is clean and well-documented
- [ ] README has clear setup instructions
- [ ] Tests pass with good coverage
- [ ] API examples provided
- [ ] Error handling is robust
- [ ] Logging is informative
- [ ] Code follows Kotlin conventions
- [ ] Git history is clean with good commit messages
- [ ] Repository is public and accessible

---

## Interview Talking Points

1. **Architecture Decisions**:
   - Why Ktor? (Lightweight, coroutines-native, Kotlin-first)
   - Layered architecture for separation of concerns
   - Interface-based design for testability

2. **Calculation Accuracy**:
   - Use of standard financial formulas
   - Annualization using compound growth
   - Consideration of trading days vs calendar days

3. **Error Handling**:
   - Custom exception hierarchy
   - Consistent error response format
   - Proper HTTP status codes

4. **Testing Strategy**:
   - Unit tests for calculations
   - Integration tests for endpoints
   - Mock external dependencies

5. **Extensibility**:
   - Easy to add new analytics
   - Pluggable data sources
   - Configurable via environment

6. **Future Improvements**:
   - Database for data persistence
   - WebSocket for real-time updates
   - Advanced analytics (ML models)
   - Multi-currency support
