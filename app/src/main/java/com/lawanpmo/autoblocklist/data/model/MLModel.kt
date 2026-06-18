package com.lawanpmo.autoblocklist.data.model

/**
 * Enum untuk tipe model ML yang tersedia
 */
enum class MLModelType {
    CNN_1D,
    RANDOM_FOREST
}

/**
 * Representasi file model yang tersedia di folder asset
 */
data class AvailableModel(
    val type: MLModelType,
    val fileName: String,
    val assetPath: String
) {
    val displayName: String get() = fileName.removeSuffix(".tflite")
}

/**
 * Data class untuk statistik model ML
 */
data class MLModelStats(
    val type: MLModelType,
    val name: String,
    val description: String
)

/**
 * Container untuk semua model yang tersedia dengan stats-nya
 */
object MLModels {
    val CNN_1D = MLModelStats(
        type = MLModelType.CNN_1D,
        name = "CNN 1D",
        description = "Convolutional Neural Network 1D untuk deteksi pola karakter domain"
    )

    val RANDOM_FOREST = MLModelStats(
        type = MLModelType.RANDOM_FOREST,
        name = "Random Forest",
        description = "Ensemble learning dengan decision trees untuk klasifikasi domain"
    )

    val all = listOf(CNN_1D, RANDOM_FOREST)

    fun getByType(type: MLModelType): MLModelStats = when (type) {
        MLModelType.CNN_1D -> CNN_1D
        MLModelType.RANDOM_FOREST -> RANDOM_FOREST
    }
}
