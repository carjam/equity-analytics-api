package com.meiken.calculator

import kotlin.math.sqrt

object StatisticalCalculations {
    
    fun mean(values: List<Double>): Double {
        require(values.isNotEmpty()) { "Cannot calculate mean of empty list" }
        return values.average()
    }
    
    fun variance(values: List<Double>): Double {
        require(values.isNotEmpty()) { "Cannot calculate variance of empty list" }
        val avg = mean(values)
        return values.sumOf { (it - avg) * (it - avg) } / values.size
    }
    
    fun standardDeviation(values: List<Double>): Double = sqrt(variance(values))
    
    fun covariance(series1: List<Double>, series2: List<Double>): Double {
        require(series1.size == series2.size) { "Series must have same length" }
        val mean1 = mean(series1)
        val mean2 = mean(series2)
        return series1.zip(series2).sumOf { (x, y) -> (x - mean1) * (y - mean2) } / series1.size
    }
    
    fun correlation(series1: List<Double>, series2: List<Double>): Double {
        val cov = covariance(series1, series2)
        val std1 = standardDeviation(series1)
        val std2 = standardDeviation(series2)
        require(std1 != 0.0 && std2 != 0.0) { "Standard deviation cannot be zero" }
        return cov / (std1 * std2)
    }
}
