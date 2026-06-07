package com.lawanpmo.autoblocklist.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Hasil ekstraksi fitur leksikal dari URL (khusus Random Forest)
 */
@Serializable
@Parcelize
data class LexicalFeatures(
    val domainLength: Int,            // 1. Panjang Domain
    val numDigits: Int,               // 2. Jumlah Angka pada Domain
    val numDots: Int,                 // 3. Jumlah Titik pada Domain
    val numDelimiters: Int,           // 4. Jumlah Delimiter / Karakter Unik
    val suspiciousWordCount: Int,     // 5. Jumlah Kata Mencurigakan (count, bukan boolean)
    val digitToLetterRatio: Float,    // 6. Rasio Angka terhadap Huruf
    val maxSequentialDigits: Int      // 7. Panjang Maksimal Digit Berurutan (count, bukan boolean)
) : Parcelable

/**
 * Record untuk setiap domain yang diblokir
 */
@Serializable
@Parcelize
data class BlockedDomainRecord(
    val id: Long = System.currentTimeMillis(),
    val domain: String,
    val timestamp: Long = System.currentTimeMillis(),
    val detectionTimeMs: Long,
    val modelUsed: String,
    val score: Float,
    val userConfirmed: Boolean = false,
    val lexicalFeatures: LexicalFeatures? = null  // Hanya ada untuk RANDOM_FOREST
) : Parcelable

/**
 * Summary statistics dari blocked domains
 */
data class BlocklistStatistics(
    val totalBlocked: Int,
    val avgDetectionTimeMs: Double,
    val blockedDomainsUnique: Int,
    val cnn1dCount: Int,
    val randomForestCount: Int,
    val mostRecentBlockedAt: Long = 0,
    val calculatedAccuracy: Double = 0.0,  // Akan dihitung dari confirmed records
    val confirmedBlockedCount: Int = 0
) {
    companion object {
        fun empty() = BlocklistStatistics(
            totalBlocked = 0,
            avgDetectionTimeMs = 0.0,
            blockedDomainsUnique = 0,
            cnn1dCount = 0,
            randomForestCount = 0,
            mostRecentBlockedAt = 0,
            calculatedAccuracy = 0.0,
            confirmedBlockedCount = 0
        )
    }
}
