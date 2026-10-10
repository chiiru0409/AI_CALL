package com.aicall.agent.data.util

object PhoneNumberUtils {
    /**
     * Normalizes a phone number into a consistent lookup key.
     * E.g. "+91 98765-43210" -> "+919876543210"
     * "09876543210" -> "+919876543210" (for 10-digit Indian numbers)
     * "9876543210" -> "+919876543210"
     */
    fun normalize(phoneNumber: String): String {
        if (phoneNumber.isBlank()) return ""
        
        // Remove all spaces, dashes, brackets, dots
        val cleaned = phoneNumber.replace(Regex("[\\s\\-\\(\\)\\.]"), "")
        
        return when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("00") -> "+" + cleaned.substring(2)
            cleaned.startsWith("0") && cleaned.length == 11 -> "+91" + cleaned.substring(1)
            cleaned.length == 10 && cleaned.all { it.isDigit() } -> "+91$cleaned"
            else -> cleaned
        }
    }
}
