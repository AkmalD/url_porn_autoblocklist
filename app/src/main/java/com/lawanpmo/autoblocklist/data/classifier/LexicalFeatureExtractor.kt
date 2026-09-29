package com.lawanpmo.autoblocklist.data.classifier

import com.lawanpmo.autoblocklist.data.model.LexicalFeatures

/**
 * Perhitungan 7 fitur leksikal RF, dipisah dari [RandomForestClassifier] (yang butuh
 * Android Context) agar bisa diuji langsung dengan JUnit murni tanpa instrumentasi.
 * Logikanya harus tetap identik dengan fungsi `extract_features()` di notebook pelatihan.
 */
object LexicalFeatureExtractor {

    private val SEQUENTIAL_DIGITS_REGEX = Regex("\\d+")

    fun extract(domain: String, suspiciousWords: Set<String>): LexicalFeatures {
        val lower = domain.lowercase()
        val digits = lower.count { it.isDigit() }
        val letters = lower.count { it.isLetter() }
        val dots = lower.count { it == '.' }
        val delimiters = lower.count { !it.isLetterOrDigit() && it != '.' }
        val suspiciousWordCount = generateNgrams(lower).count { it in suspiciousWords }
        val digitToLetterRatio = if (letters > 0) digits.toFloat() / letters.toFloat() else digits.toFloat()
        val maxSequentialDigits = SEQUENTIAL_DIGITS_REGEX.findAll(lower).maxOfOrNull { it.value.length } ?: 0

        return LexicalFeatures(
            domainLength = domain.length,
            numDigits = digits,
            numDots = dots,
            numDelimiters = delimiters,
            suspiciousWordCount = suspiciousWordCount,
            digitToLetterRatio = digitToLetterRatio,
            maxSequentialDigits = maxSequentialDigits
        )
    }

    /** Semua substring unik panjang 2..6 dari [lower] (harus sudah lowercase). */
    fun generateNgrams(lower: String, minLen: Int = 2, maxLen: Int = 6): Set<String> {
        val grams = HashSet<String>()
        for (n in minLen..maxLen) {
            if (lower.length < n) continue
            for (i in 0..lower.length - n) {
                grams.add(lower.substring(i, i + n))
            }
        }
        return grams
    }
}
