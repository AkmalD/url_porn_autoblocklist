package com.lawanpmo.autoblocklist.data.classifier

import android.util.Log
import com.lawanpmo.autoblocklist.data.model.MLModelType
import com.lawanpmo.autoblocklist.domain.repository.IUrlClassifier
import com.lawanpmo.autoblocklist.domain.repository.UrlClassificationResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager untuk mengelola multiple classifiers dan memungkinkan switching antar model.
 */
@Singleton
class ClassifierManager @Inject constructor(
    private val cnnClassifier: UrlClassifier,
    private val rfClassifier: RandomForestClassifier
) {
    companion object {
        private const val TAG = "ClassifierManager"
    }

    private var currentModelType: MLModelType = MLModelType.CNN_1D
    private var currentClassifier: IUrlClassifier = cnnClassifier
    private var isInitialized = false

    /**
     * Switch ke model yang berbeda
     */
    suspend fun switchModel(modelType: MLModelType): Boolean {
        if (currentModelType == modelType && isInitialized) {
            Log.i(TAG, "═══════════════════════════════════════════")
            Log.i(TAG, "Already using model: ${modelType.name}")
            Log.i(TAG, "═══════════════════════════════════════════")
            return true
        }

        return try {
            Log.w(TAG, "╔═══════════════════════════════════════════╗")
            Log.w(TAG, "║ SWITCHING MODEL: ${modelType.name.padEnd(28)}║")
            Log.w(TAG, "╚═══════════════════════════════════════════╝")
            
            val newClassifier = when (modelType) {
                MLModelType.CNN_1D -> cnnClassifier
                MLModelType.RANDOM_FOREST -> rfClassifier
            }

            // Load model if not already loaded
            if (!newClassifier.isModelLoaded()) {
                val modelPath = when (modelType) {
                    MLModelType.CNN_1D -> "url_classifier.tflite"
                    MLModelType.RANDOM_FOREST -> "url_classifier_rf.tflite"
                }

                Log.d(TAG, "Loading model from: $modelPath")
                if (!newClassifier.loadModel(modelPath)) {
                    Log.e(TAG, "❌ Failed to load model: $modelType")
                    return false
                }
            }

            currentModelType = modelType
            currentClassifier = newClassifier
            isInitialized = true
            Log.i(TAG, "✅ Successfully switched to: ${modelType.name}")
            Log.i(TAG, "═══════════════════════════════════════════")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error switching model to $modelType", e)
            false
        }
    }

    /**
     * Get current model type
     */
    fun getCurrentModelType(): MLModelType = currentModelType

    /**
     * Classify URL using current model
     */
    fun classify(url: String): UrlClassificationResult {
        val result = currentClassifier.classify(url)
        Log.i(
            TAG,
            "🔍 [${currentModelType.name}] Classified '$url' → Score: ${"%.4f".format(result.score)}, Adult: ${result.isAdult}, Time: ${result.inferenceTimeMs}ms"
        )
        return result
    }

    /**
     * Check if current model is loaded
     */
    fun isModelLoaded(): Boolean = currentClassifier.isModelLoaded()

    /**
     * Release all resources
     */
    fun release() {
        cnnClassifier.release()
        rfClassifier.release()
        Log.d(TAG, "All classifiers released")
    }
}
