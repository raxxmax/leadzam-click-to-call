package com.rahul.leadzam

/**
 * Normalizes and validates phone numbers.
 *
 * Why a separate file?  Pure functions are easy to unit-test without Android.
 */

// E.164 format: "+" then a non-zero leading digit, then 6–14 more digits.
// Total digits: 7–15 (the "+" is not a digit).
// Examples: "+919876543210"  "+15551234567"
private val E164_REGEX = Regex("""^\+[1-9]\d{6,14}$""")

/**
 * Strips common non-digit characters (spaces, dashes, parentheses, dots)
 * then validates the result as E.164.
 *
 * @return the cleaned number if valid, or null if it cannot be normalized.
 */
fun normalizeAndValidatePhone(raw: String?): String? {
    if (raw.isNullOrBlank()) return null

    // Remove spaces, dashes, parentheses, dots — common in formatted numbers.
    val cleaned = raw.replace(Regex("""[\s\-().]+"""), "")

    // Must match E.164 after cleaning.
    return if (E164_REGEX.matches(cleaned)) cleaned else null
}
