package com.meiken.validator

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutputValidatorTest {

    // --- Sharpe ---

    @Test
    fun `checkSharpe returns null for values within normal range`() {
        assertNull(OutputValidator.checkSharpe(0.0))
        assertNull(OutputValidator.checkSharpe(1.5))
        assertNull(OutputValidator.checkSharpe(-1.0))
        assertNull(OutputValidator.checkSharpe(4.9))
        assertNull(OutputValidator.checkSharpe(-4.9))
    }

    @Test
    fun `checkSharpe returns warning for values outside normal range`() {
        assertNotNull(OutputValidator.checkSharpe(6.0))
        assertNotNull(OutputValidator.checkSharpe(-6.0))
        assertTrue(OutputValidator.checkSharpe(10.0)!!.contains("sharpe_implausible"))
    }

    @Test
    fun `checkSharpe returns warning for non-finite values`() {
        assertTrue(OutputValidator.checkSharpe(Double.NaN)!!.contains("not_finite"))
        assertTrue(OutputValidator.checkSharpe(Double.POSITIVE_INFINITY)!!.contains("not_finite"))
        assertTrue(OutputValidator.checkSharpe(Double.NEGATIVE_INFINITY)!!.contains("not_finite"))
    }

    // --- Beta ---

    @Test
    fun `checkBeta returns null for values within normal range`() {
        assertNull(OutputValidator.checkBeta(1.0))
        assertNull(OutputValidator.checkBeta(-1.5))
        assertNull(OutputValidator.checkBeta(2.9))
        assertNull(OutputValidator.checkBeta(-2.9))
    }

    @Test
    fun `checkBeta returns warning for values outside normal range`() {
        assertNotNull(OutputValidator.checkBeta(5.0))
        assertNotNull(OutputValidator.checkBeta(-5.0))
        assertTrue(OutputValidator.checkBeta(4.0)!!.contains("beta_implausible"))
    }

    @Test
    fun `checkBeta returns warning for non-finite values`() {
        assertTrue(OutputValidator.checkBeta(Double.NaN)!!.contains("not_finite"))
    }

    // --- Alpha ---

    @Test
    fun `checkAlpha returns null for values within normal range`() {
        assertNull(OutputValidator.checkAlpha(0.05))
        assertNull(OutputValidator.checkAlpha(-0.1))
        assertNull(OutputValidator.checkAlpha(0.99))
    }

    @Test
    fun `checkAlpha returns warning for values outside normal range`() {
        assertNotNull(OutputValidator.checkAlpha(2.0))
        assertNotNull(OutputValidator.checkAlpha(-2.0))
        assertTrue(OutputValidator.checkAlpha(1.5)!!.contains("alpha_implausible"))
    }

    @Test
    fun `checkAlpha returns warning for non-finite values`() {
        assertTrue(OutputValidator.checkAlpha(Double.NaN)!!.contains("not_finite"))
    }

    // --- Annualized Volatility ---

    @Test
    fun `checkAnnualizedVolatility returns null for values within normal range`() {
        assertNull(OutputValidator.checkAnnualizedVolatility(0.15))
        assertNull(OutputValidator.checkAnnualizedVolatility(0.5))
        assertNull(OutputValidator.checkAnnualizedVolatility(0.05))
        assertNull(OutputValidator.checkAnnualizedVolatility(3.0))
    }

    @Test
    fun `checkAnnualizedVolatility returns warning for near-zero volatility`() {
        assertNotNull(OutputValidator.checkAnnualizedVolatility(0.01))
        assertTrue(OutputValidator.checkAnnualizedVolatility(0.01)!!.contains("volatility_implausible"))
    }

    @Test
    fun `checkAnnualizedVolatility returns warning for extreme high volatility`() {
        assertNotNull(OutputValidator.checkAnnualizedVolatility(5.0))
        assertTrue(OutputValidator.checkAnnualizedVolatility(5.0)!!.contains("volatility_implausible"))
    }

    @Test
    fun `checkAnnualizedVolatility returns warning for non-finite values`() {
        assertTrue(OutputValidator.checkAnnualizedVolatility(Double.NaN)!!.contains("not_finite"))
    }

    // --- Max Drawdown ---

    @Test
    fun `checkMaxDrawdown returns null for values within normal range`() {
        assertNull(OutputValidator.checkMaxDrawdown(0.0))
        assertNull(OutputValidator.checkMaxDrawdown(0.3))
        assertNull(OutputValidator.checkMaxDrawdown(0.99))
    }

    @Test
    fun `checkMaxDrawdown returns warning for negative drawdown`() {
        assertNotNull(OutputValidator.checkMaxDrawdown(-0.01))
        assertTrue(OutputValidator.checkMaxDrawdown(-0.5)!!.contains("max_drawdown_negative"))
    }

    @Test
    fun `checkMaxDrawdown returns warning for extreme drawdown above 99 percent`() {
        assertNotNull(OutputValidator.checkMaxDrawdown(1.0))
        assertTrue(OutputValidator.checkMaxDrawdown(1.0)!!.contains("max_drawdown_extreme"))
    }

    @Test
    fun `checkMaxDrawdown returns warning for non-finite values`() {
        assertTrue(OutputValidator.checkMaxDrawdown(Double.NaN)!!.contains("not_finite"))
    }

    // --- Sortino ---

    @Test
    fun `checkSortino returns null for values within normal range`() {
        assertNull(OutputValidator.checkSortino(0.0))
        assertNull(OutputValidator.checkSortino(2.5))
        assertNull(OutputValidator.checkSortino(-2.5))
        assertNull(OutputValidator.checkSortino(4.9))
    }

    @Test
    fun `checkSortino returns warning for values outside normal range`() {
        assertNotNull(OutputValidator.checkSortino(6.0))
        assertNotNull(OutputValidator.checkSortino(-6.0))
        assertTrue(OutputValidator.checkSortino(10.0)!!.contains("sortino_implausible"))
    }

    @Test
    fun `checkSortino returns warning for non-finite values`() {
        assertTrue(OutputValidator.checkSortino(Double.NaN)!!.contains("not_finite"))
        assertTrue(OutputValidator.checkSortino(Double.POSITIVE_INFINITY)!!.contains("not_finite"))
    }

    // --- Calmar ---

    @Test
    fun `checkCalmar returns null for values within normal range`() {
        assertNull(OutputValidator.checkCalmar(0.0))
        assertNull(OutputValidator.checkCalmar(5.0))
        assertNull(OutputValidator.checkCalmar(-5.0))
        assertNull(OutputValidator.checkCalmar(10.0))
    }

    @Test
    fun `checkCalmar returns null for positive infinity (zero drawdown case)`() {
        assertNull(OutputValidator.checkCalmar(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `checkCalmar returns warning for values outside normal range`() {
        assertNotNull(OutputValidator.checkCalmar(11.0))
        assertNotNull(OutputValidator.checkCalmar(-11.0))
        assertTrue(OutputValidator.checkCalmar(20.0)!!.contains("calmar_implausible"))
    }

    @Test
    fun `checkCalmar returns warning for NaN`() {
        assertTrue(OutputValidator.checkCalmar(Double.NaN)!!.contains("calmar_not_defined"))
    }

    // --- Warning string format ---

    @Test
    fun `warning strings include the actual value`() {
        val w = OutputValidator.checkSharpe(12.345)!!
        assertTrue(w.contains("12.35") || w.contains("12.34"), "expected value in warning: $w")
    }
}
