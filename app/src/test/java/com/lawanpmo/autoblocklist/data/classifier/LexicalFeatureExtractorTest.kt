package com.lawanpmo.autoblocklist.data.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Membandingkan [LexicalFeatureExtractor] dengan fitur acuan Python
 * (expected_features.csv, dibangkitkan oleh scripts/generate_expected_features.py).
 */
class LexicalFeatureExtractorTest {

    companion object {
        private const val F6_TOLERANCE = 1e-5f

        // Working directory unit test Gradle = folder modul (app/)
        private const val WORDS_PATH = "src/main/assets/rf/kata_sensitif_android.txt"

        private lateinit var suspiciousWords: Set<String>

        @BeforeClass
        @JvmStatic
        fun loadWords() {
            suspiciousWords = File(WORDS_PATH).useLines { lines ->
                lines.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toHashSet()
            }
        }
    }

    @Test
    fun wordListLoaded() {
        assertEquals(10_000, suspiciousWords.size)
        // Daftar memuat entri 5–6 karakter, jadi n-gram harus sampai panjang 6
        assertTrue("porn.c" in suspiciousWords)
        assertTrue("x.com" in suspiciousWords)
    }

    @Test
    fun knownExample() {
        val f = LexicalFeatureExtractor.extract("akademik.polban.ac.id", suspiciousWords)
        assertEquals(21, f.domainLength)
        assertEquals(0, f.numDigits)
        assertEquals(3, f.numDots)
        assertEquals(0, f.numDelimiters)
        assertEquals(0f, f.digitToLetterRatio, F6_TOLERANCE)
        assertEquals(0, f.maxSequentialDigits)
    }

    @Test
    fun ngramsCoverLengthTwoToSix() {
        val grams = LexicalFeatureExtractor.generateNgrams("abcdefg")
        assertEquals(setOf(2, 3, 4, 5, 6), grams.map { it.length }.toSet())
        assertEquals(6 + 5 + 4 + 3 + 2, grams.size)
    }

    @Test
    fun matchesPythonReferenceFor200Domains() {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("expected_features.csv")) {
            "expected_features.csv tidak ditemukan di test resources"
        }
        val rows = stream.bufferedReader().useLines { lines -> lines.drop(1).filter { it.isNotBlank() }.toList() }
        assertEquals(200, rows.size)

        val mismatches = mutableListOf<String>()
        for (row in rows) {
            val c = row.split(",")
            val domain = c[0]
            val f = LexicalFeatureExtractor.extract(domain, suspiciousWords)
            val intsOk = f.domainLength == c[1].toInt() &&
                f.numDigits == c[2].toInt() &&
                f.numDots == c[3].toInt() &&
                f.numDelimiters == c[4].toInt() &&
                f.suspiciousWordCount == c[5].toInt() &&
                f.maxSequentialDigits == c[7].toInt()
            val f6Ok = Math.abs(f.digitToLetterRatio - c[6].toFloat()) <= F6_TOLERANCE
            if (!intsOk || !f6Ok) {
                mismatches += "$domain: expected ${c.drop(1)} got [${f.domainLength}, ${f.numDigits}, " +
                    "${f.numDots}, ${f.numDelimiters}, ${f.suspiciousWordCount}, " +
                    "${f.digitToLetterRatio}, ${f.maxSequentialDigits}]"
            }
        }
        println("Unit test fitur RF: ${rows.size - mismatches.size}/${rows.size} baris cocok")
        assertTrue("Baris tidak cocok (${mismatches.size}):\n" + mismatches.joinToString("\n"), mismatches.isEmpty())
    }
}
