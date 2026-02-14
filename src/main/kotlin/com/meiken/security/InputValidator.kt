package com.meiken.security

import com.meiken.error.BadRequestException
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

private val SYMBOL_REGEX = Regex("^[A-Z0-9]{1,5}$")
private val CONTROL_CHARS = Regex("[\\x00-\\x1F\\x7F]")
private const val MAX_STRING_LENGTH = 100

/**
 * Validates and sanitizes API inputs. Use before business logic.
 * - Ticker symbols: 1-5 uppercase alphanumeric only.
 * - Dates: ISO-8601 (YYYY-MM-DD), not in future, reasonable range.
 * - Strings: control characters removed, max length enforced.
 */
object InputValidator {

    /**
     * Validates ticker symbol: 1-5 uppercase alphanumeric characters.
     * @throws BadRequestException if invalid
     */
    fun validateSymbol(value: String?, paramName: String = "symbol"): String {
        if (value.isNullOrBlank()) throw BadRequestException("$paramName is required")
        val sanitized = sanitizeString(value).uppercase()
        if (!sanitized.matches(SYMBOL_REGEX)) {
            throw BadRequestException("$paramName must be 1-5 alphanumeric characters (uppercase)")
        }
        return sanitized
    }

    /**
     * Validates date string: YYYY-MM-DD, not in future.
     * @throws BadRequestException if invalid
     */
    fun validateDate(value: String?, paramName: String = "date"): LocalDate {
        if (value.isNullOrBlank()) throw BadRequestException("$paramName is required")
        val sanitized = sanitizeString(value)
        val date = try {
            LocalDate.parse(sanitized)
        } catch (e: Exception) {
            throw BadRequestException("Invalid date format for $paramName: expected YYYY-MM-DD")
        }
        val today = Clock.System.todayIn(TimeZone.UTC)
        if (date > today) {
            throw BadRequestException("$paramName cannot be in the future")
        }
        return date
    }

    /**
     * Sanitizes string: removes control characters and trims; enforces max length.
     * Use for any user-provided string to prevent control-character injection.
     */
    fun sanitizeString(value: String?, maxLength: Int = MAX_STRING_LENGTH): String {
        if (value == null) return ""
        val withoutControl = value.replace(CONTROL_CHARS, "")
        val trimmed = withoutControl.trim()
        return if (trimmed.length > maxLength) trimmed.take(maxLength) else trimmed
    }

    /**
     * Prevents path traversal: ensures path has no ".." or absolute path segments.
     * Use when constructing file paths from user input (this API has no file ops; for future use).
     */
    fun preventPathTraversal(path: String?): String {
        if (path.isNullOrBlank()) return ""
        val sanitized = sanitizeString(path, 500)
        if (sanitized.contains("..") || sanitized.startsWith("/") || sanitized.contains("\\")) {
            throw BadRequestException("Invalid path")
        }
        return sanitized
    }
}
