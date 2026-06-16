package com.lawanpmo.autoblocklist.data.model

enum class EvalModelChoice { CNN_1D, RANDOM_FOREST }

data class TestUrlItem(
    val url: String,
    val isAdult: Boolean
)

data class SingleModelTestResult(
    val url: String,
    val groundTruth: Boolean,
    val prediction: Boolean,
    val score: Float,
    val inferenceTimeMs: Double,
    val lexicalFeatures: LexicalFeatures? = null
) {
    val correct: Boolean get() = prediction == groundTruth
}

data class ModelEvaluationMetrics(
    val accuracy: Float,
    val precision: Float,
    val recall: Float,
    val f1Score: Float,
    val avgInferenceTimeMs: Double,
    val totalInferenceTimeMs: Double,
    val truePositives: Int,
    val trueNegatives: Int,
    val falsePositives: Int,
    val falseNegatives: Int
) {
    companion object {
        fun fromResults(results: List<SingleModelTestResult>): ModelEvaluationMetrics {
            var tp = 0; var tn = 0; var fp = 0; var fn = 0
            var totalTime = 0.0
            for (r in results) {
                totalTime += r.inferenceTimeMs
                when {
                    r.groundTruth && r.prediction   -> tp++
                    !r.groundTruth && !r.prediction -> tn++
                    !r.groundTruth && r.prediction  -> fp++
                    r.groundTruth && !r.prediction  -> fn++
                }
            }
            val accuracy  = if (results.isNotEmpty()) (tp + tn).toFloat() / results.size else 0f
            val precision = if (tp + fp > 0) tp.toFloat() / (tp + fp) else 0f
            val recall    = if (tp + fn > 0) tp.toFloat() / (tp + fn) else 0f
            val f1        = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0f
            return ModelEvaluationMetrics(
                accuracy             = accuracy,
                precision            = precision,
                recall               = recall,
                f1Score              = f1,
                avgInferenceTimeMs   = if (results.isNotEmpty()) totalTime / results.size else 0.0,
                totalInferenceTimeMs = totalTime,
                truePositives        = tp,
                trueNegatives        = tn,
                falsePositives       = fp,
                falseNegatives       = fn
            )
        }
    }
}

val DEFAULT_TEST_URLS = listOf(
    TestUrlItem("google.com",        false),
    TestUrlItem("facebook.com",      false),
    TestUrlItem("youtube.com",       false),
    TestUrlItem("wikipedia.org",     false),
    TestUrlItem("github.com",        false),
    TestUrlItem("stackoverflow.com", false),
    TestUrlItem("amazon.com",        false),
    TestUrlItem("twitter.com",       false),
    TestUrlItem("instagram.com",     false),
    TestUrlItem("kompas.com",        false),
    TestUrlItem("pornhub.com",       true),
    TestUrlItem("xvideos.com",       true),
    TestUrlItem("xnxx.com",          true),
    TestUrlItem("xhamster.com",      true),
    TestUrlItem("redtube.com",       true),
    TestUrlItem("youporn.com",       true),
    TestUrlItem("brazzers.com",      true),
    TestUrlItem("sex.com",           true),
    TestUrlItem("bokep.com",         true),
    TestUrlItem("hentai.tv",         true)
)
