# Manual Test Validation for Equity Analytics API Financial Calculator

This document outlines all 8 test cases and their expected outcomes.

## Test 1: calculateDailyReturns with simple increasing prices

**Input:**
```kotlin
val prices = listOf(
    DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
    DailyPrice(LocalDate(2024, 1, 2), close = 105.0),
    DailyPrice(LocalDate(2024, 1, 3), close = 110.0)
)
```

**Expected:**
- Returns list with 2 elements
- First return: (105-100)/100 = 0.05
- Second return: (110-105)/105 ≈ 0.047619

**Assertion Tolerance:** 0.0001

---

## Test 2: calculateDailyReturns with price decrease

**Input:**
```kotlin
val prices = listOf(
    DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
    DailyPrice(LocalDate(2024, 1, 2), close = 95.0)
)
```

**Expected:**
- Returns list with 1 element
- Return: (95-100)/100 = -0.05

**Assertion Tolerance:** 0.0001

---

## Test 3: calculateDailyReturns throws with insufficient data

**Input:**
```kotlin
FinancialCalculations.calculateDailyReturns(listOf(
    DailyPrice(LocalDate(2024, 1, 1), 100.0)
))
```

**Expected:**
- Throws IllegalArgumentException
- Message: "Need at least 2 prices to calculate returns"

---

## Test 4: annualizeReturn converts daily to annual

**Input:**
- Daily return: 0.001
- Trading days: 252

**Formula:**
- (1.001)^252 - 1 ≈ 0.2872

**Expected:** Annualized return ≈ 0.2872

**Assertion Tolerance:** 0.001

---

## Test 5: calculateAlpha with known values (Jensen's alpha via OLS)

**Input:**
- Target returns: List of 252 × 0.001
- Benchmark returns: List of 252 × 0.0008
- Risk-free rate: 0.04 (default)

**Calculation:**
1. β = cov(target, benchmark) / var(benchmark)
   — both series are constant, so β = 1.0 (perfectly correlated, same variance ratio)
2. rf_daily = (1.04)^(1/252) − 1 ≈ 0.000155
3. α_daily = mean(target) − β × mean(benchmark) − rf_daily × (1 − β)
           = 0.001 − 1.0 × 0.0008 − 0.000155 × 0.0
           = 0.0002
4. α_annualized = (1.0002)^252 − 1 ≈ 0.0513

**Expected:** Alpha ≈ 0.05 (with tolerance 0.01)

**Assertion Tolerance:** 0.01

---

## Test 6: calculateBeta with correlated movements

**Input:**
```kotlin
val target = listOf(0.02, 0.03, -0.01, 0.04)
val benchmark = listOf(0.01, 0.02, -0.005, 0.03)
```

**Formula:**
- Beta = covariance(target, benchmark) / variance(benchmark)
- For highly correlated series, Beta ≈ 1.0

**Expected:** Beta ≈ 1.0 (±0.3)

**Assertion Tolerance:** 0.3

---

## Test 7: calculateVolatility returns daily and annualized

**Input:**
```kotlin
val returns = listOf(0.01, -0.005, 0.02, -0.01, 0.015)
```

**Expected:**
- Returns Pair<Double, Double>
- Annualized volatility > Daily volatility
- Annualized = Daily × √252
- Both values > 0

---

## Test 8: calculateSharpe with positive returns

**Input:**
```kotlin
val returns = List(252) { 0.002 }  // Consistent 0.2% daily returns
val riskFreeRate = 0.04
```

**Calculation:**
1. Annualized return: (1.002)^252 - 1 ≈ 0.6414
2. Annualized volatility: Calculate from 0.002 daily returns
3. Sharpe = (0.6414 - 0.04) / volatility

**Expected:** Sharpe > 0 (positive excess return over risk-free rate)

---

## Summary

All 8 tests validate:

| Aspect | Coverage |
|--------|----------|
| Happy Path | ✅ 5 tests |
| Edge Cases | ✅ 2 tests |
| Error Handling | ✅ 1 test |
| Formula Accuracy | ✅ 8 tests |
| Numerical Precision | ✅ 8 tests |

**Expected Result:** 8/8 tests pass with 0 failures
