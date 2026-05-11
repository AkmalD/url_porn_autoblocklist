package com.lawanpmo.autoblocklist.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.lawanpmo.autoblocklist.data.model.BlockedDomainRecord
import com.lawanpmo.autoblocklist.data.model.BlocklistStatistics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository untuk manage blocked domain history
 */
@Singleton
class BlocklistRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val TAG = "BlocklistRepository"
        private const val PREF_NAME = "blocklist_history"
        private const val KEY_BLOCKED_DOMAINS = "blocked_domains_json"
        private const val MAX_HISTORY_SIZE = 1000  // Keep last 1000 records
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val json = Json

    /**
     * Add blocked domain record to history
     */
    fun addBlockedDomain(record: BlockedDomainRecord) {
        try {
            val records = getAllBlockedDomains().toMutableList()
            records.add(0, record)  // Add to front (newest first)
            
            // Keep only last MAX_HISTORY_SIZE records
            if (records.size > MAX_HISTORY_SIZE) {
                records.subList(MAX_HISTORY_SIZE, records.size).clear()
            }

            val json = json.encodeToString(records)
            prefs.edit().putString(KEY_BLOCKED_DOMAINS, json).apply()
            
            Log.d(TAG, "Added blocked domain: ${record.domain} (detection: ${record.detectionTimeMs}ms)")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding blocked domain", e)
        }
    }

    /**
     * Get all blocked domain records
     */
    fun getAllBlockedDomains(): List<BlockedDomainRecord> {
        return try {
            val json = prefs.getString(KEY_BLOCKED_DOMAINS, null) ?: return emptyList()
            Json.decodeFromString<List<BlockedDomainRecord>>(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting blocked domains", e)
            emptyList()
        }
    }

    /**
     * Get blocked domains for specific model
     */
    fun getBlockedDomainsByModel(modelName: String): List<BlockedDomainRecord> {
        return getAllBlockedDomains().filter { it.modelUsed == modelName }
    }

    /**
     * Get recent blocked domains (last N records)
     */
    fun getRecentBlockedDomains(limit: Int = 10): List<BlockedDomainRecord> {
        return getAllBlockedDomains().take(limit)
    }

    /**
     * Calculate statistics from history
     */
    fun getStatistics(): BlocklistStatistics {
        val records = getAllBlockedDomains()
        
        if (records.isEmpty()) {
            return BlocklistStatistics.empty()
        }

        val totalBlocked = records.size
        val avgDetectionTimeMs = records.map { it.detectionTimeMs }.average()
        val blockedDomainsUnique = records.map { it.domain }.distinct().size
        val cnn1dCount = records.count { it.modelUsed.contains("CNN", ignoreCase = true) }
        val randomForestCount = records.count { it.modelUsed.contains("RANDOM", ignoreCase = true) }
        val mostRecentBlockedAt = records.firstOrNull()?.timestamp ?: 0
        
        // Calculate accuracy from confirmed records
        val confirmedRecords = records.filter { it.userConfirmed }
        val confirmedBlockedCount = confirmedRecords.size
        val calculatedAccuracy = if (confirmedRecords.isNotEmpty()) {
            // For now, if user confirmed it was blocked correctly, count as accurate
            (confirmedBlockedCount.toDouble() / totalBlocked.toDouble()) * 100
        } else {
            0.0
        }

        return BlocklistStatistics(
            totalBlocked = totalBlocked,
            avgDetectionTimeMs = avgDetectionTimeMs,
            blockedDomainsUnique = blockedDomainsUnique,
            cnn1dCount = cnn1dCount,
            randomForestCount = randomForestCount,
            mostRecentBlockedAt = mostRecentBlockedAt,
            calculatedAccuracy = calculatedAccuracy,
            confirmedBlockedCount = confirmedBlockedCount
        )
    }

    /**
     * Mark record as confirmed by user
     */
    fun confirmBlockedDomain(recordId: Long) {
        try {
            val records = getAllBlockedDomains().toMutableList()
            val index = records.indexOfFirst { it.id == recordId }
            if (index >= 0) {
                records[index] = records[index].copy(userConfirmed = true)
                val json = Json.encodeToString(records)
                prefs.edit().putString(KEY_BLOCKED_DOMAINS, json).apply()
                Log.d(TAG, "Confirmed blocked domain record: $recordId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error confirming blocked domain", e)
        }
    }

    /**
     * Clear all history
     */
    fun clearHistory() {
        try {
            prefs.edit().remove(KEY_BLOCKED_DOMAINS).apply()
            Log.d(TAG, "Cleared all blocked domain history")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing history", e)
        }
    }

    /**
     * Get unique blocked domains
     */
    fun getUniqueBlockedDomains(): List<String> {
        return getAllBlockedDomains().map { it.domain }.distinct()
    }
}
