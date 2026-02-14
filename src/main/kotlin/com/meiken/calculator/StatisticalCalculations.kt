package com.meiken.calculator

import kotlin.math.sqrt

/** Basic statistics: mean, variance, std dev, covariance, Pearson correlation. */
object StatisticalCalculations {

    fun mean(values: List<Double>): Double {
        require(values.isNotEmpty()) { "Cannot calculate mean of empty list" }
        return values.average()
    }

    /** Population variance: sum of squared deviations from mean, divided by size. */
    fun variance(values: List<Double>): Double {
        require(values.isNotEmpty()) { "Cannot calculate variance of empty list" }
        val avg = mean(values)
        return values.sumOf { (it - avg) * (it - avg) } / values.size
    }

    fun standardDeviation(values: List<Double>): Double = sqrt(variance(values))

    /** Population covariance: E[(X - mean(X))(Y - mean(Y))]; series must have same length. */
    fun covariance(series1: List<Double>, series2: List<Double>): Double {
        require(series1.size == series2.size) { "Series must have same length" }
        val mean1 = mean(series1)
        val mean2 = mean(series2)
        return series1.zip(series2).sumOf { (x, y) -> (x - mean1) * (y - mean2) } / series1.size
    }

    /** Pearson correlation: covariance / (std1 * std2). Fails if either std dev is zero. */
    fun correlation(series1: List<Double>, series2: List<Double>): Double {
        val cov = covariance(series1, series2)
        val std1 = standardDeviation(series1)
        val std2 = standardDeviation(series2)
        require(std1 != 0.0 && std2 != 0.0) { "Standard deviation cannot be zero" }
        return cov / (std1 * std2)
    }
}
