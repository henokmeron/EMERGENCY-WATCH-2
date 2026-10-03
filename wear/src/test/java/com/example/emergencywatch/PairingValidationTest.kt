package com.example.emergencywatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingValidationTest {

    @Test
    fun testPairingCodeNormalization() {
        val rawCode = " X8K9 L2P4 "
        val cleanCode = rawCode.replace(" ", "").trim()
        assertEquals("X8K9L2P4", cleanCode)
        assertEquals(8, cleanCode.length)
    }

    @Test
    fun testCodeLengthValidation() {
        fun isValidLength(code: String): Boolean {
            val clean = code.replace(" ", "").trim()
            return clean.length in 6..20
        }

        assertTrue(isValidLength("123456"))
        assertTrue(isValidLength("X8K9L2P4"))
        assertFalse(isValidLength("12345"))
        assertFalse(isValidLength("123456789012345678901"))
    }
}
