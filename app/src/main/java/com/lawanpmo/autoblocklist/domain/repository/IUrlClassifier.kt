package com.lawanpmo.autoblocklist.domain.repository

/**
 * Result of URL classification
 */
data class UrlClassificationResult(
    val score: Float,
    val isAdult: Boolean,
    val inferenceTimeMs: Long,
    val domain: String,
    val skipped: Boolean = false  // True if input was skipped (not a valid domain)
) {
    companion object {
        fun error(domain: String) = UrlClassificationResult(
            score = 0f,
            isAdult = false,
            inferenceTimeMs = 0,
            domain = domain,
            skipped = false
        )
    }
}

/**
 * Interface for URL classification using ML
 */
interface IUrlClassifier {
    suspend fun loadModel(modelPath: String): Boolean
    fun classify(url: String): UrlClassificationResult
    fun isModelLoaded(): Boolean
    fun release()
}
