package com.meiken.security

import com.meiken.error.BadRequestException
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InputValidatorTest {

    @Test
    fun `validateSymbol accepts valid symbol`() {
        assertEquals("AAPL", InputValidator.validateSymbol("AAPL"))
        assertEquals("SPY", InputValidator.validateSymbol("spy"))
    }

    @Test
    fun `validateSymbol throws when null or blank`() {
        assertFailsWith<BadRequestException> { InputValidator.validateSymbol(null) }
        assertFailsWith<BadRequestException> { InputValidator.validateSymbol("") }
        assertFailsWith<BadRequestException> { InputValidator.validateSymbol("   ") }
    }

    @Test
    fun `validateSymbol throws when invalid format`() {
        assertFailsWith<BadRequestException> { InputValidator.validateSymbol("TOOLONG") }
        assertFailsWith<BadRequestException> { InputValidator.validateSymbol("aa-pl") }
    }

    @Test
    fun `validateSymbol respects maxLength by truncating`() {
        val longSymbol = "A".repeat(200)
        assertEquals("AAAAA", InputValidator.validateSymbol(longSymbol, maxLength = 5))
    }

    @Test
    fun `validateSymbol throws when truncated value has invalid chars`() {
        val invalidAfterTruncate = "AB".repeat(3) + "-"
        assertFailsWith<BadRequestException> { InputValidator.validateSymbol(invalidAfterTruncate, maxLength = 7) }
    }

    @Test
    fun `validateDate throws when invalid format`() {
        assertFailsWith<BadRequestException> { InputValidator.validateDate("not-a-date") }
        assertFailsWith<BadRequestException> { InputValidator.validateDate("2024/01/01") }
    }

    @Test
    fun `validateDate throws when future`() {
        val nextYear = (Clock.System.todayIn(TimeZone.UTC).year + 1).toString() + "-06-15"
        assertFailsWith<BadRequestException> { InputValidator.validateDate(nextYear) }
    }

    @Test
    fun `sanitizeString returns empty for null`() {
        assertEquals("", InputValidator.sanitizeString(null))
    }

    @Test
    fun `sanitizeString removes control characters`() {
        assertEquals("ab", InputValidator.sanitizeString("a\u0000b"))
        assertEquals("xy", InputValidator.sanitizeString("x\u001Fy"))
    }

    @Test
    fun `sanitizeString trims and enforces maxLength`() {
        assertEquals("abc", InputValidator.sanitizeString("  abc  ", maxLength = 10))
        assertEquals("ab", InputValidator.sanitizeString("abcdef", maxLength = 2))
    }

    @Test
    fun `preventPathTraversal returns empty for blank`() {
        assertEquals("", InputValidator.preventPathTraversal(null))
        assertEquals("", InputValidator.preventPathTraversal(""))
    }

    @Test
    fun `preventPathTraversal throws for dot dot`() {
        assertFailsWith<BadRequestException> { InputValidator.preventPathTraversal("foo/../bar") }
    }

    @Test
    fun `preventPathTraversal throws for absolute path`() {
        assertFailsWith<BadRequestException> { InputValidator.preventPathTraversal("/etc/passwd") }
        assertFailsWith<BadRequestException> { InputValidator.preventPathTraversal("\\windows\\path") }
    }

    @Test
    fun `preventPathTraversal returns sanitized valid path`() {
        assertEquals("safe/path", InputValidator.preventPathTraversal("safe/path"))
    }
}
