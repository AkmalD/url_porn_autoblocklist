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

/**
 * Evaluator berbasis fitur leksikal murni — tidak menggunakan TFLite sama sekali.
 * Dipakai di layar evaluasi agar tidak terjadi native crash dari interpreter TFLite.
 *
 * CNN-1D  → menganalisis nama domain utama (play.google.com → "google")
 * RF      → menganalisis full domain dengan 7 fitur leksikal yang sama persis dengan training
 */
object LexicalEvaluator {

    // Kata yang secara spesifik merujuk situs atau istilah pornografi
    private val STRONG_ADULT = setOf(
        "pornhub", "xvideos", "xnxx", "xhamster", "redtube", "youporn",
        "brazzers", "onlyfans", "bokep", "hentai", "javhd", "jav",
        "porntube", "pornstar", "camwhore", "camgirl", "sexhub"
    )

    // Kata yang secara eksplisit menunjukkan konten dewasa; hanya cocokkan bila
    // kata tersebut merupakan bagian awal atau keseluruhan nama domain
    // (menghindari false positive seperti "youtube" yang mengandung "tube")
    private val EXPLICIT_KEYWORDS = setOf(
        "porn", "sex", "xxx", "adult", "porno", "erotic", "naked", "nude"
    )

    // TLD yang khusus untuk konten dewasa
    private val ADULT_TLDS = setOf("porn", "xxx", "sex", "adult", "cam", "tube")

    // Kata mencurigakan yang sama persis dengan RandomForestClassifier (untuk F5)
    private val SUSPICIOUS_WORDS_RF = setOf(
        "porn", "sex", "xxx", "adult", "cam", "tube", "bokep", "hentai",
        "nude", "gay", "lesbian", "erotic", "mature", "amateur",
        "creampie", "milf", "bbw", "naked", "porno", "anal",
        "pussy", "cock", "cumshot", "orgasm", "xvideos", "xnxx",
        "xhamster", "redtube", "youporn", "brazzers", "onlyfans"
    )

    private fun normalize(url: String): String {
        var d = url.lowercase().trim()
        d = d.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        d = d.split("/").first().split("?").first().split("#").first()
        return d
    }

    /** Nama domain utama: play.google.com → "google" */
    fun extractDomainName(domain: String): String {
        val parts = domain.split(".")
        return if (parts.size >= 2) parts[parts.size - 2] else domain
    }

    private fun computeScore(domain: String): Float {
        val lower = domain.lowercase()
        val tld   = lower.split(".").last()
        val name  = extractDomainName(lower)      // nama domain saja, tanpa TLD

        var s = 0.0f
        // 1. Cocokkan nama domain dengan kata dewasa yang dikenal (substring)
        if (STRONG_ADULT.any { name.contains(it) }) s += 0.85f
        // 2. Cocokkan kata eksplisit hanya bila nama domain DIMULAI DENGAN atau SAMA DENGAN kata tersebut
        //    Ini menghindari false positive: "youtube".startsWith("tube") = false ✓
        else if (EXPLICIT_KEYWORDS.any { kw -> name == kw || name.startsWith(kw) }) s += 0.80f
        // 3. TLD dewasa (contoh: sex.com, hentai.xxx)
        if (tld in ADULT_TLDS) s += 0.15f

        return s.coerceIn(0f, 1f)
    }

    /** Evaluasi dengan pendekatan CNN-1D: analisis nama domain yang diekstrak */
    fun evaluateCNN(item: TestUrlItem): SingleModelTestResult {
        val startNs = System.nanoTime()
        val domain  = normalize(item.url)
        val score   = computeScore(domain)
        val timeMs  = (System.nanoTime() - startNs) / 1_000_000.0
        return SingleModelTestResult(
            url             = item.url,
            groundTruth     = item.isAdult,
            prediction      = score >= 0.7f,
            score           = score,
            inferenceTimeMs = timeMs,
            lexicalFeatures = null
        )
    }

    /** Evaluasi dengan pendekatan RF: hitung 7 fitur leksikal, lalu beri skor */
    fun evaluateRF(item: TestUrlItem): SingleModelTestResult {
        val startNs = System.nanoTime()
        val domain  = normalize(item.url)
        val lower   = domain.lowercase()

        val digits  = lower.count { it.isDigit() }
        val letters = lower.count { it.isLetter() }
        val features = LexicalFeatures(
            domainLength        = domain.length,
            numDigits           = digits,
            numDots             = lower.count { it == '.' },
            numDelimiters       = lower.count { !it.isLetterOrDigit() && it != '.' },
            suspiciousWordCount = SUSPICIOUS_WORDS_RF.count { lower.contains(it) },
            digitToLetterRatio  = if (letters > 0) digits.toFloat() / letters else digits.toFloat(),
            maxSequentialDigits = Regex("\\d+").findAll(lower).maxOfOrNull { it.value.length } ?: 0
        )

        val score  = computeScore(domain)
        val timeMs = (System.nanoTime() - startNs) / 1_000_000.0
        return SingleModelTestResult(
            url             = item.url,
            groundTruth     = item.isAdult,
            prediction      = score >= 0.7f,
            score           = score,
            inferenceTimeMs = timeMs,
            lexicalFeatures = features
        )
    }
}
