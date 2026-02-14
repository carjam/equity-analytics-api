# Meiken - Financial Analytics REST API

**Meiken** (銘柄検索) - Ticker Lookup & Financial Analytics

A REST API for computing comprehensive financial analytics on stock market data, including returns, alpha, beta, volatility, Sharpe ratio, and correlation analysis.

## 📊 Phase 4: Calculation Engine ✅ COMPLETE

### What's Implemented

#### Model Classes
- **DailyPrice** - Historical pricing data (date, close, open, high, low, volume)
- **DailyReturn** - Calculated daily returns from price changes

#### Financial Calculations (6 methods)
1. **calculateDailyReturns** - Compute daily percentage returns
2. **annualizeReturn** - Convert daily to annualized returns (252 trading days)
3. **calculateAlpha** - Excess return vs benchmark
4. **calculateBeta** - Systematic risk measure
5. **calculateVolatility** - Both daily and annualized
6. **calculateSharpe** - Risk-adjusted returns

#### Statistical Calculations (5 methods)
1. **mean** - Arithmetic average
2. **variance** - Distribution spread
3. **standardDeviation** - Volatility measure
4. **covariance** - Joint variability between series
5. **correlation** - Normalized relationship (-1 to 1)

#### Test Suite (8 tests)
Comprehensive unit tests covering:
- Happy path scenarios
- Edge cases (negative returns, decreasing prices)
- Error handling (insufficient data)
- Numerical accuracy validation

### Technical Stack

- **Language**: Kotlin 1.8.20
- **Build**: Gradle 7.6.1 or Maven 3.9+
- **Testing**: JUnit 5 (Jupiter)
- **Date Handling**: kotlinx.datetime
- **Serialization**: kotlinx.serialization
- **Target JVM**: Java 11+

## 🚀 Quick Start

### Build with Maven (Recommended)
```bash
mvn clean test
```

### Build with Gradle
```bash
gradle clean test --no-daemon
```

### Verify Implementation (No Build Tool)
```bash
bash verify.sh
```

## 📚 Documentation

- **[docs/QUICK_START.md](docs/QUICK_START.md)** - Get building in 5 minutes
- **[docs/BUILD.md](docs/BUILD.md)** - Detailed build troubleshooting
- **[docs/DIAGNOSTICS.md](docs/DIAGNOSTICS.md)** - Diagnostic guide
- **[docs/IMPLEMENTATION_REPORT.md](docs/IMPLEMENTATION_REPORT.md)** - Complete implementation details
- **[docs/PHASE4_COMPLETE.md](docs/PHASE4_COMPLETE.md)** - Phase 4 summary
- **[docs/PHASE4_STATUS.md](docs/PHASE4_STATUS.md)** - Implementation status
- **[docs/SPEC.md](docs/SPEC.md)** - API specification
- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** - System design
- **[docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md)** - Implementation guide

## 📁 Project Structure

```
meiken/
├── src/
│   ├── main/kotlin/com/meiken/
│   │   ├── model/
│   │   │   ├── DailyPrice.kt          ✅
│   │   │   └── DailyReturn.kt         ✅
│   │   └── calculator/
│   │       ├── FinancialCalculations.kt ✅
│   │       └── StatisticalCalculations.kt ✅
│   └── test/kotlin/com/meiken/calculator/
│       └── FinancialCalculationsTest.kt ✅ (8 tests)
├── build.gradle.kts                    ✅
├── pom.xml                             ✅
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew                             
└── docs/
    ├── SPEC.md
    ├── ARCHITECTURE.md
    └── IMPLEMENTATION.md
```

## 🧮 Financial Formulas

All formulas properly implemented and tested:

| Metric | Formula | Implementation |
|--------|---------|-----------------|
| Daily Return | (P_t - P_{t-1}) / P_{t-1} | `calculateDailyReturns()` |
| Annualized Return | (1 + r_daily)^252 - 1 | `annualizeReturn()` |
| Alpha | R_target - R_benchmark (annualized) | `calculateAlpha()` |
| Beta | Cov(target, benchmark) / Var(benchmark) | `calculateBeta()` |
| Volatility | σ_daily × √252 | `calculateVolatility()` |
| Sharpe Ratio | (R_p - R_f) / σ_p | `calculateSharpe()` |

## ✅ Implementation Checklist

- [x] 5 Kotlin source files created
- [x] 2 model classes with @Serializable
- [x] 2 calculator classes (11 total methods)
- [x] 8 comprehensive unit tests
- [x] Financial formula implementation
- [x] Statistical calculations
- [x] Input validation & error handling
- [x] Gradle build configuration
- [x] Maven build configuration
- [x] Documentation (6 files)

## 🧪 Running Tests

### Maven
```bash
mvn clean test
```

### Gradle
```bash
gradle clean test
gradle test --tests FinancialCalculationsTest
gradle test --tests FinancialCalculationsTest.*Daily*
```

### Expected Output
```
Tests run: 8, Failures: 0, Errors: 0 ✅
```

## 🔨 Build Troubleshooting

If you encounter build errors:

1. **Clear cache**
   ```bash
   gradle --stop
   rm -rf .gradle build ~/.gradle
   ```

2. **Use Maven** (more reliable)
   ```bash
   apt-get install maven
   mvn clean test
   ```

3. **Verify implementation** (no build tool needed)
   ```bash
   bash verify.sh
   ```

See **[DIAGNOSTICS.md](DIAGNOSTICS.md)** for detailed troubleshooting.

## 📋 Test Coverage

**8 Unit Tests:**
1. ✅ Daily returns with increasing prices
2. ✅ Daily returns with decreasing prices
3. ✅ Error on insufficient data
4. ✅ Annualized return formula
5. ✅ Alpha calculation
6. ✅ Beta calculation
7. ✅ Volatility calculation
8. ✅ Sharpe ratio calculation

## 🎯 Next Phase: Phase 5 (Service Layer)

After Phase 4 completion, Phase 5 will implement:
- ReturnsService
- AlphaService
- Input validation layer
- Service integration tests

## 📝 License

See [LICENSE](LICENSE) for details.

## 🤝 Contributing

This project follows TDD (Test-Driven Development) methodology. Each phase includes comprehensive unit tests.

---

**Status**: ✅ Phase 4 Complete - Ready for Phase 5
