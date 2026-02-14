# Building and Testing Meiken Phase 4

## Issues & Solutions

If you encounter the Gradle error:
```
Failed to query the value of property 'buildFlowServiceProperty'
Could not isolate value org.gradle.api.internal.HasConvention
```

This is a Kotlin Gradle Plugin compatibility issue. Try these solutions in order:

## Solution 1: Clean Gradle Cache (Quick Fix)

```bash
gradle --stop
rm -rf .gradle build
gradle clean test --no-daemon
```

## Solution 2: Use Maven (Alternative Build System)

Maven typically handles Kotlin builds more reliably:

```bash
mvn clean test
```

If Maven isn't installed:
```bash
apt-get install maven
mvn clean test
```

## Solution 3: Update Dependencies

The build configuration has been updated to use compatible versions:
- Kotlin: 1.8.20 (stable, no BuildFlowService issues)
- kotlinx-datetime: 0.4.0
- kotlinx-serialization: 1.5.0  
- JUnit: 5.9.2
- Java target: 11

## Solution 4: Manual Directory Cleanup

```bash
rm -rf ~/.gradle
rm -rf /workspaces/meiken/.gradle
rm -rf /workspaces/meiken/build
gradle clean test
```

## Solution 5: Offline Build

If you have network issues:
```bash
gradle --offline clean test
```

## Solution 6: Verbose Output for Debugging

```bash
gradle clean test --info --stacktrace
gradle clean test --debug  # Even more verbose
```

## Manual Verification (No Build Tool)

If all else fails, verify the implementation manually:

```bash
bash verify.sh
```

This checks:
- All 5 source files exist
- All 11 calculator methods implemented
- All 8 test cases present
- All required imports (kotlinx.datetime, kotlinx.serialization)
- All financial formulas in place

## Build Tool Availability Check

```bash
which gradle   # Check Gradle
which mvn      # Check Maven
which kotlinc  # Check Kotlin Compiler
java -version  # Check Java
```

## Files to Check

If build fails, verify these critical files exist and are correct:

1. **Build Config**: `build.gradle.kts` or `pom.xml`
2. **Kotlin Version**: Should be 1.8.20 or 1.8.22 (NOT 1.9.x)
3. **Source Files**:
   - `src/main/kotlin/com/meiken/model/DailyPrice.kt`
   - `src/main/kotlin/com/meiken/model/DailyReturn.kt`
   - `src/main/kotlin/com/meiken/calculator/FinancialCalculations.kt`
   - `src/main/kotlin/com/meiken/calculator/StatisticalCalculations.kt`
   - `src/test/kotlin/com/meiken/calculator/FinancialCalculationsTest.kt`

## Troubleshooting Commands

| Problem | Solution |
|---------|----------|
| Gradle daemon stuck | `gradle --stop` |
| Cache corrupted | `rm -rf ~/.gradle` |
| Java not found | `apt-get install openjdk-11-jdk` |
| Gradle not found | `apt-get install gradle` |
| Maven not found | `apt-get install maven` |
| Kotlin version conflict | Use Kotlin 1.8.20 exactly |

## After Build Succeeds

You should see output like:
```
BUILD SUCCESSFUL in Xm Ys
8 tests completed, 0 failed
```

If you see failures, they will be listed with the test name and assertion error.

## Implementation Status

✅ **All source files created**
- 2 Model classes
- 2 Calculator classes (11 total methods)
- 1 Test file (8 tests)

✅ **All financial formulas implemented**
- Daily returns calculation
- Annualized returns (252 trading days)
- Alpha (target vs benchmark returns)
- Beta (covariance/variance)
- Volatility (daily and annualized)
- Sharpe ratio (risk-adjusted returns)

✅ **All statistical methods implemented**
- Mean, Variance, Standard Deviation
- Covariance, Correlation

✅ **Build configuration available**
- Gradle build (build.gradle.kts)
- Maven build (pom.xml)

## Docker Alternative

If local build continues to fail, you can build in Docker:

```bash
docker run -it --rm -v /workspaces/meiken:/app openjdk:11 bash
cd /app
apt-get update && apt-get install -y gradle
gradle clean test
```
