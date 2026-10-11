package com.aicall.agent.data.util

object PhoneNumberUtils {
    private val EMERGENCY_NUMBERS = setOf(
        "112", "100", "101", "102", "108", "1090", "1091", "1098", "911", "999"
    )

    private val WITHHELD_IDENTIFIERS = setOf(
        "private", "unknown", "restricted", "withheld", "anonymous", "unavailable", "p"
    )

    /**
     * Identifies if a number is an emergency service code that must never be blocked or screened.
     */
    fun isEmergencyNumber(phoneNumber: String): Boolean {
        val cleaned = phoneNumber.replace(Regex("[\\s\\-\\(\\)\\.]"), "").trim()
        if (cleaned.isBlank()) return false
        val bareDigits = cleaned.removePrefix("+").removePrefix("91")
        return EMERGENCY_NUMBERS.contains(cleaned) || EMERGENCY_NUMBERS.contains(bareDigits)
    }

    /**
     * Identifies if a caller number is withheld, private, or anonymous.
     */
    fun isWithheldNumber(phoneNumber: String): Boolean {
        val trimmed = phoneNumber.trim().lowercase()
        return trimmed.isBlank() || WITHHELD_IDENTIFIERS.contains(trimmed)
    }

    /**
     * Normalizes a phone number into a consistent lookup key.
     * E.g. "+91 98765-43210" -> "+919876543210"
     * "09876543210" -> "+919876543210" (for 10-digit Indian numbers)
     * "9876543210" -> "+919876543210"
     * Preserves short codes and emergency numbers as raw digits.
     */
    fun normalize(phoneNumber: String): String {
        if (phoneNumber.isBlank()) return ""

        val trimmed = phoneNumber.trim()
        if (WITHHELD_IDENTIFIERS.contains(trimmed.lowercase())) {
            return "Unknown"
        }

        // Remove all spaces, dashes, brackets, dots
        val cleaned = trimmed.replace(Regex("[\\s\\-\\(\\)\\.]"), "")

        // Emergency and carrier short codes (<= 5 digits) must NOT have country codes prepended
        if (cleaned.length <= 5 && cleaned.all { it.isDigit() }) {
            return cleaned
        }

        return when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("00") -> "+" + cleaned.substring(2)
            cleaned.startsWith("0") && cleaned.length == 11 -> "+91" + cleaned.substring(1)
            cleaned.length == 10 && cleaned.all { it.isDigit() } -> "+91$cleaned"
            else -> cleaned
        }
    }
}
