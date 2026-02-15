package com.meiken.validator

import com.meiken.model.DailyPrice
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataValidatorTest {

    @Test
    fun `validatePriceData filters unrealistic low price`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 0.005),
            DailyPrice(LocalDate(2024, 1, 3), close = 105.0)
        )
        val result = DataValidator.validatePriceData(prices, "AAPL")
        assertEquals(2, result.cleanedPrices.size)
        assertEquals(100.0, result.cleanedPrices[0].close)
        assertEquals(105.0, result.cleanedPrices[1].close)
        assertTrue(result.issues.any { it.type == "unrealistic_price" })
    }

    @Test
    fun `validatePriceData filters unrealistic high price`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 2_000_000.0),
            DailyPrice(LocalDate(2024, 1, 3), close = 105.0)
        )
        val result = DataValidator.validatePriceData(prices, "AAPL")
        assertEquals(2, result.cleanedPrices.size)
        assertTrue(result.issues.any { it.type == "unrealistic_price" })
    }

    @Test
    fun `validatePriceData deduplicates by date keeping latest`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 101.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 102.0)
        )
        val result = DataValidator.validatePriceData(prices)
        assertEquals(2, result.cleanedPrices.size)
        assertEquals(102.0, result.cleanedPrices[1].close)
        assertTrue(result.issues.any { it.type == "duplicate_date" })
    }

    @Test
    fun `validatePriceData clamps negative volume to null`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0, volume = 1_000_000L),
            DailyPrice(LocalDate(2024, 1, 2), close = 101.0, volume = -100L)
        )
        val result = DataValidator.validatePriceData(prices)
        assertEquals(2, result.cleanedPrices.size)
        assertEquals(null, result.cleanedPrices[1].volume)
        assertTrue(result.issues.any { it.type == "negative_volume" })
    }

    @Test
    fun `validatePriceData sorts by date`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 3), close = 103.0),
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 101.0)
        )
        val result = DataValidator.validatePriceData(prices)
        assertEquals(LocalDate(2024, 1, 1), result.cleanedPrices[0].date)
        assertEquals(LocalDate(2024, 1, 2), result.cleanedPrices[1].date)
        assertEquals(LocalDate(2024, 1, 3), result.cleanedPrices[2].date)
    }

    @Test
    fun `validatePriceData empty list returns empty`() {
        val result = DataValidator.validatePriceData(emptyList())
        assertEquals(0, result.cleanedPrices.size)
        assertEquals(0, result.issues.size)
    }

    @Test
    fun `detectOutliers returns indices beyond 3 sigma`() {
        val returns = List(99) { 0.01 } + 10.0
        val indices = DataValidator.detectOutliers(returns)
        assertTrue(indices.isNotEmpty(), "outlier 10.0 should be detected")
        assertTrue(99 in indices, "outlier at index 99")
    }

    @Test
    fun `detectOutliers returns empty for small list`() {
        assertEquals(emptyList(), DataValidator.detectOutliers(listOf(0.01, 0.02)))
    }

    @Test
    fun `detectOutliers returns empty when no outliers`() {
        val returns = List(20) { 0.01 }
        assertEquals(emptyList(), DataValidator.detectOutliers(returns))
    }

    @Test
    fun `detectOutliers with custom sigma uses given sigma`() {
        val returns = List(50) { 0.0 } + 2.0
        val withDefaultSigma = DataValidator.detectOutliers(returns)
        val withWideSigma = DataValidator.detectOutliers(returns, sigma = 5.0)
        assertTrue(withDefaultSigma.isNotEmpty())
        assertTrue(withWideSigma.size <= withDefaultSigma.size)
    }

    @Test
    fun `validatePriceData with config uses config thresholds`() {
        val config = com.meiken.config.DataQualityConfig(
            minPrice = 1.0,
            maxPrice = 100.0,
            maxSingleDayChangePct = 0.10,
            outlierSigma = 2.0
        )
        val prices = listOf(
            com.meiken.model.DailyPrice(LocalDate(2024, 1, 1), 50.0),
            com.meiken.model.DailyPrice(LocalDate(2024, 1, 2), 0.5)
        )
        val result = DataValidator.validatePriceData(prices, "T", config)
        assertEquals(1, result.cleanedPrices.size)
        assertEquals(50.0, result.cleanedPrices[0].close)
    }
}
