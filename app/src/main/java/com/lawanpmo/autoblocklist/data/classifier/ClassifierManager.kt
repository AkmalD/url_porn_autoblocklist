package com.lawanpmo.autoblocklist.data.classifier

import android.content.Context
import android.util.Log
import com.lawanpmo.autoblocklist.data.model.AvailableModel
import com.lawanpmo.autoblocklist.data.model.MLModelType
import com.lawanpmo.autoblocklist.domain.repository.IUrlClassifier
import com.lawanpmo.autoblocklist.domain.repository.UrlClassificationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassifierManager @Inject constructor(
    private val cnnClassifier: UrlClassifier,
    private val rfClassifier: RandomForestClassifier,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ClassifierManager"
        const val CNN_FOLDER = "cnn"
        const val RF_FOLDER = "rf"
    }

    private var currentModelType: MLModelType = MLModelType.CNN_1D
    private var currentModelFile: String = ""
    private var currentClassifier: IUrlClassifier = cnnClassifier
    private var isInitialized = false

    // Track which file each classifier currently has loaded
    private var loadedCnnFile: String = ""
    private var loadedRfFile: String = ""

    /**
     * List all .tflite files in the asset subfolder for the given model type.
     * Adding a new file to assets/cnn/ or assets/rf/ makes it appear here automatically.
     */
    fun getAvailableModels(type: MLModelType): List<AvailableModel> {
        val folder = folderFor(type)
        return try {
            (context.assets.list(folder) ?: emptyArray())
                .filter { it.endsWith(".tflite") }
                .sorted()
                .map { fileName -> AvailableModel(type, fileName, "$folder/$fileName") }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list models in $folder", e)
            emptyList()
        }
    }

    /**
     * Switch to the specified model type and file.
     * If [fileName] is null/blank, the first available file in the folder is used.
     */
    suspend fun switchModel(type: MLModelType, fileName: String? = null): Boolean {
        val resolvedFile = resolveFileName(type, fileName) ?: run {
            Log.e(TAG, "No models available for ${type.name} in ${folderFor(type)}/")
            return false
        }

        if (currentModelType == type && currentModelFile == resolvedFile && isInitialized) {
            Log.i(TAG, "Already using: ${type.name}/$resolvedFile")
            return true
        }

        return try {
            Log.w(TAG, "╔═══════════════════════════════════════════╗")
            Log.w(TAG, "║ SWITCHING → ${type.name}/$resolvedFile".padEnd(44) + "║")
            Log.w(TAG, "╚═══════════════════════════════════════════╝")

            val assetPath = "${folderFor(type)}/$resolvedFile"
            val newClassifier = classifierFor(type)

            val needsReload = when (type) {
                MLModelType.CNN_1D -> loadedCnnFile != resolvedFile
                MLModelType.RANDOM_FOREST -> loadedRfFile != resolvedFile
            }

            if (needsReload) {
                Log.d(TAG, "Loading model file: $assetPath")
                val success = newClassifier.loadModel(assetPath)
                if (!success) {
                    Log.e(TAG, "❌ Failed to load: $assetPath")
                    return false
                }
                when (type) {
                    MLModelType.CNN_1D -> loadedCnnFile = resolvedFile
                    MLModelType.RANDOM_FOREST -> loadedRfFile = resolvedFile
                }
            }

            currentModelType = type
            currentModelFile = resolvedFile
            currentClassifier = newClassifier
            isInitialized = true
            Log.i(TAG, "✅ Active model: ${type.name}/$resolvedFile")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error switching model to ${type.name}/$resolvedFile", e)
            false
        }
    }

    fun getCurrentModelType(): MLModelType = currentModelType
    fun getCurrentModelFile(): String = currentModelFile

    fun classify(url: String): UrlClassificationResult {
        val result = currentClassifier.classify(url)
        Log.i(
            TAG,
            "🔍 [${currentModelType.name}/$currentModelFile] '$url' → Score: ${"%.4f".format(result.score)}, Adult: ${result.isAdult}, Time: ${result.inferenceTimeMs}ms"
        )
        return result
    }

    fun isModelLoaded(): Boolean = currentClassifier.isModelLoaded()

    fun release() {
        cnnClassifier.release()
        rfClassifier.release()
        Log.d(TAG, "All classifiers released")
    }

    private fun resolveFileName(type: MLModelType, fileName: String?): String? {
        if (!fileName.isNullOrBlank()) return fileName
        return getAvailableModels(type).firstOrNull()?.fileName
    }

    private fun folderFor(type: MLModelType) =
        if (type == MLModelType.CNN_1D) CNN_FOLDER else RF_FOLDER

    private fun classifierFor(type: MLModelType): IUrlClassifier =
        if (type == MLModelType.CNN_1D) cnnClassifier else rfClassifier
}
