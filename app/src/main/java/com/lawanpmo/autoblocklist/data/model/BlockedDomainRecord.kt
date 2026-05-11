package com.lawanpmo.autoblocklist.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Record untuk setiap domain yang diblokir
 */
@Serializable
@Parcelize
data class BlockedDomainRecord(
    val id: Long = System.currentTimeMillis(),
    val domain: String,
    val timestamp: Long = System.currentTimeMillis(),
    val detectionTimeMs: Long,  // Waktu deteksi dalam ms
    val modelUsed: String,  // CNN_1D atau RANDOM_FOREST
    val score: Float,  // Classification score (0.0 - 1.0)
    val userConfirmed: Boolean = false  // Apakah user confirm bahwa ini memang harus diblokir
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
