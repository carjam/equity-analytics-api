package com.meiken.calculator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class StatisticalCalculationsTest {

    @Test
    fun `mean with single value`() {
        assertEquals(5.0, StatisticalCalculations.mean(listOf(5.0)), 0.0001)
    }

    @Test
    fun `mean with multiple values`() {
        assertEquals(3.0, StatisticalCalculations.mean(listOf(1.0, 2.0, 3.0, 4.0, 5.0)), 0.0001)
    }

    @Test
    fun `mean with empty list throws`() {
        assertThrows<IllegalArgumentException> {
            StatisticalCalculations.mean(emptyList())
        }
    }

    @Test
    fun `variance with single value is zero`() {
        assertEquals(0.0, StatisticalCalculations.variance(listOf(10.0)), 0.0001)
    }

    @Test
    fun `variance with known values`() {
        // Values 2, 4, 4, 4, 5, 5, 7, 9 -> mean = 5, variance = 4
        val values = listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)
        assertEquals(4.0, StatisticalCalculations.variance(values), 0.0001)
    }

    @Test
    fun `variance with empty list throws`() {
        assertThrows<IllegalArgumentException> {
            StatisticalCalculations.variance(emptyList())
        }
    }

    @Test
    fun `standardDeviation is sqrt of variance`() {
        // Variance of 4 -> std dev 2
        assertEquals(2.0, StatisticalCalculations.standardDeviation(listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)), 0.0001)
    }

    @Test
    fun `covariance with known values`() {
        // Same series -> covariance = variance
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val cov = StatisticalCalculations.covariance(values, values)
        assertEquals(StatisticalCalculations.variance(values), cov, 0.0001)
    }

    @Test
    fun `covariance with different length series throws`() {
        assertThrows<IllegalArgumentException> {
            StatisticalCalculations.covariance(listOf(1.0, 2.0), listOf(1.0))
        }
    }

    @Test
    fun `correlation of same series is one`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        assertEquals(1.0, StatisticalCalculations.correlation(values, values), 0.0001)
    }

    @Test
    fun `correlation with zero variance throws`() {
        val constant = listOf(5.0, 5.0, 5.0)
        assertThrows<IllegalArgumentException> {
            StatisticalCalculations.correlation(constant, listOf(1.0, 2.0, 3.0))
        }
    }
}
