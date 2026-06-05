package com.lawanpmo.autoblocklist.data.model

data class TestUrlItem(
    val url: String,
    val isAdult: Boolean
)

data class UrlTestResult(
    val url: String,
    val groundTruth: Boolean,
    val cnnPrediction: Boolean,
    val cnnScore: Float,
    val cnnTimeMs: Long,
    val rfPrediction: Boolean,
    val rfScore: Float,
    val rfTimeMs: Long,
    val rfLexicalFeatures: LexicalFeatures? = null
) {
    val cnnCorrect: Boolean get() = cnnPrediction == groundTruth
    val rfCorrect: Boolean get() = rfPrediction == groundTruth
}

data class ModelEvaluationMetrics(
    val accuracy: Float,
    val precision: Float,
    val recall: Float,
    val f1Score: Float,
    val avgInferenceTimeMs: Double,
    val totalInferenceTimeMs: Long,
    val truePositives: Int,
    val trueNegatives: Int,
    val falsePositives: Int,
    val falseNegatives: Int
) {
    companion object {
        fun fromResults(results: List<UrlTestResult>, useCnn: Boolean): ModelEvaluationMetrics {
            var tp = 0; var tn = 0; var fp = 0; var fn = 0
            var totalTime = 0L
            for (r in results) {
                val pred = if (useCnn) r.cnnPrediction else r.rfPrediction
                totalTime += if (useCnn) r.cnnTimeMs else r.rfTimeMs
                when {
                    r.groundTruth && pred -> tp++
                    !r.groundTruth && !pred -> tn++
                    !r.groundTruth && pred -> fp++
                    r.groundTruth && !pred -> fn++
                }
            }
            val accuracy = if (results.isNotEmpty()) (tp + tn).toFloat() / results.size else 0f
            val precision = if (tp + fp > 0) tp.toFloat() / (tp + fp) else 0f
            val recall = if (tp + fn > 0) tp.toFloat() / (tp + fn) else 0f
            val f1 = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0f
            return ModelEvaluationMetrics(
                accuracy = accuracy,
                precision = precision,
                recall = recall,
                f1Score = f1,
                avgInferenceTimeMs = if (results.isNotEmpty()) totalTime.toDouble() / results.size else 0.0,
                totalInferenceTimeMs = totalTime,
                truePositives = tp,
                trueNegatives = tn,
                falsePositives = fp,
                falseNegatives = fn
            )
        }
    }
}

val DEFAULT_TEST_URLS = listOf(
    TestUrlItem("google.com", false),
    TestUrlItem("facebook.com", false),
    TestUrlItem("youtube.com", false),
    TestUrlItem("wikipedia.org", false),
    TestUrlItem("github.com", false),
    TestUrlItem("stackoverflow.com", false),
    TestUrlItem("amazon.com", false),
    TestUrlItem("twitter.com", false),
    TestUrlItem("instagram.com", false),
    TestUrlItem("kompas.com", false),
    TestUrlItem("pornhub.com", true),
    TestUrlItem("xvideos.com", true),
    TestUrlItem("xnxx.com", true),
    TestUrlItem("xhamster.com", true),
    TestUrlItem("redtube.com", true),
    TestUrlItem("youporn.com", true),
    TestUrlItem("brazzers.com", true),
    TestUrlItem("sex.com", true),
    TestUrlItem("bokep.com", true),
    TestUrlItem("hentai.tv", true)
)
