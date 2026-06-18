package com.lawanpmo.autoblocklist.data.classifier

import android.content.Context
import android.util.Log
import com.lawanpmo.autoblocklist.data.model.LexicalFeatures
import com.lawanpmo.autoblocklist.domain.repository.IUrlClassifier
import com.lawanpmo.autoblocklist.domain.repository.UrlClassificationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RandomForestClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) : IUrlClassifier {

    companion object {
        private const val TAG = "RFClassifier"
        private const val DEFAULT_THRESHOLD = 0.7f
        private const val NUM_FEATURES = 7

        // HARUS identik dengan SUSPICIOUS_WORDS di feature_extraction.py (training/tuning).
        // Daftar sudah dedup: jika sebuah kata adalah substring kata lain, hanya yang
        // lebih pendek dipertahankan agar F5 (suspicious_word_count) tidak hitung-ganda.
        private val SUSPICIOUS_WORDS = setOf(
            // --- Konten Eksplisit Umum ---
            "porn", "xxx", "sex", "nude", "naked", "hentai", "erotic",
            "fetish", "orgasm", "hardcore", "softcore",
            // --- Bagian Tubuh / Istilah Seksual ---
            "boobs", "tits", "bigtit", "bigass", "butt", "cock", "dick",
            "penis", "pussy", "vagina", "anal", "anus", "clitoris",
            "nipple", "breast",
            // --- Aktivitas Seksual ---
            "fuck", "blowjob", "handjob", "footjob", "gangbang", "threesome",
            "orgy", "cumshot", "creampie", "masturbat", "fingering", "rimming",
            "fisting", "squirt", "bdsm", "bondage", "spank", "whip", "dildo",
            "vibrator", "deepthroat", "ejaculat", "intercourse", "brazzer",
            "baise",  // Bahasa Prancis
            // --- Kategori / Genre ---
            "milf", "teen", "mature", "amateur", "lesbian", "gay", "shemale",
            "tranny", "ladyboy", "interracial", "incest", "voyeur", "upskirt",
            "escort", "prostitut", "hooker", "stripper", "webcam", "camgirl",
            "livecam",
            // --- Istilah Lainnya ---
            "whore", "bitch", "naughty", "horny", "swinger", "adult",
            "xvideos", "xnxx", "redtube", "xhamster", "freeport",
            "jav",
            "salope", "putain", "pute",  // Bahasa Prancis
            "bukake", "bukkake", "memek", "ngentot", "kontol", "titit",
            // Leet-speak umum
            "p0rn", "s3x", "pr0n", "h3nt4i", "j4v",
            // --- Tambahan khusus app (tidak ada di list Python, tanpa konflik substring) ---
            "bokep", "bbw", "onlyfans"
        )

        private val COMMON_TLDS = setOf(
            "com", "net", "org", "info", "biz", "co", "io", "me", "tv", "cc",
            "in", "ru", "cn", "jp", "kr", "de", "uk", "fr", "it", "es", "br",
            "au", "ca", "nl", "id", "my", "th", "ph", "vn", "sg", "hk", "tw",
            "xyz", "top", "site", "online", "club", "live", "fun", "space",
            "tech", "store", "shop", "app", "dev", "cloud", "media",
            "porn", "sex", "xxx", "adult", "cam", "tube",
            "tk", "ml", "ga", "cf", "gq", "ws", "pw", "vip"
        )

        private val SECOND_LEVEL_TLDS = setOf(
            "co.id", "co.uk", "co.jp", "co.kr", "co.nz", "co.za", "co.in", "co.th",
            "com.au", "com.br", "com.cn", "com.hk", "com.my", "com.sg", "com.tw",
            "com.vn", "com.ph", "com.ar", "com.mx",
            "net.id", "net.au", "org.id", "ac.id", "go.id", "or.id",
            "sch.id", "web.id", "my.id", "biz.id"
        )
    }

    private var interpreter: Interpreter? = null

    // Dideteksi saat loadModel — mencegah buffer size mismatch yang menyebabkan native crash
    private var modelInputElements: Int = NUM_FEATURES
    private var modelOutputElements: Int = 1

    override suspend fun loadModel(modelPath: String): Boolean {
        release()
        return try {
            Log.d(TAG, "🌳 Loading Random Forest TFLite model: $modelPath")
            val actualPath = resolveModelPath(modelPath) ?: return false
            val options = Interpreter.Options().apply { setNumThreads(2) }
            interpreter = Interpreter(File(actualPath), options)

            // Deteksi input/output shape dari model yang sebenarnya
            val inputShape  = interpreter!!.getInputTensor(0).shape()
            val outputShape = interpreter!!.getOutputTensor(0).shape()

            modelInputElements  = inputShape.fold(1) { acc, i -> acc * i }
            modelOutputElements = outputShape.fold(1) { acc, i -> acc * i }

            Log.i(TAG, "✅ RF model loaded")
            Log.d(TAG, "   Input : ${inputShape.contentToString()} → $modelInputElements elements")
            Log.d(TAG, "   Output: ${outputShape.contentToString()} → $modelOutputElements elements")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "❌ Failed to load RF model", e)
            false
        }
    }

    @Synchronized
    override fun classify(url: String): UrlClassificationResult {
        val startNs = System.nanoTime()
        val fullDomain = normalizeDomain(url)

        if (!isDomainValid(fullDomain)) {
            return UrlClassificationResult(
                score = 0.0f,
                isAdult = false,
                inferenceTimeMs = (System.nanoTime() - startNs) / 1_000_000.0,
                domain = fullDomain,
                skipped = true
            )
        }

        // Ekstrak fitur di luar try-catch agar selalu tersedia di semua jalur return
        val features = extractLexicalFeatures(fullDomain)

        Log.d(TAG, "Fitur '$fullDomain': len=${features.domainLength} digits=${features.numDigits} " +
            "dots=${features.numDots} delims=${features.numDelimiters} " +
            "suspCount=${features.suspiciousWordCount} d/l=${"%.3f".format(features.digitToLetterRatio)} " +
            "maxSeqDig=${features.maxSequentialDigits}")

        val interp = interpreter
        if (interp == null) {
            Log.w(TAG, "Model not loaded — returning features without score")
            return UrlClassificationResult(
                score = 0f,
                isAdult = false,
                inferenceTimeMs = (System.nanoTime() - startNs) / 1_000_000.0,
                domain = fullDomain,
                lexicalFeatures = features
            )
        }

        return try {
            val inputBuffer  = buildInputBuffer(features, modelInputElements)
            val outputBuffer = ByteBuffer.allocateDirect(modelOutputElements * 4).order(ByteOrder.nativeOrder())

            interp.run(inputBuffer, outputBuffer)

            outputBuffer.rewind()
            val score = if (modelOutputElements >= 2) {
                outputBuffer.float  // skip p_safe
                outputBuffer.float  // p_adult
            } else {
                outputBuffer.float
            }

            val inferenceTime = (System.nanoTime() - startNs) / 1_000_000.0
            val isAdult = score > DEFAULT_THRESHOLD

            Log.d(TAG, "RF '$fullDomain' → score=${"%.4f".format(score)} isAdult=$isAdult time=${"%.3f".format(inferenceTime)}ms")

            UrlClassificationResult(
                score = score,
                isAdult = isAdult,
                inferenceTimeMs = inferenceTime,
                domain = fullDomain,
                lexicalFeatures = features
            )
        } catch (e: Throwable) {
            Log.e(TAG, "RF inference error for '$fullDomain': ${e.message}")
            // Tetap kembalikan features meskipun inferensi gagal
            UrlClassificationResult(
                score = 0f,
                isAdult = false,
                inferenceTimeMs = (System.nanoTime() - startNs) / 1_000_000.0,
                domain = fullDomain,
                lexicalFeatures = features
            )
        }
    }

    override fun isModelLoaded(): Boolean = interpreter != null

    override fun release() {
        try { interpreter?.close() } catch (e: Exception) { Log.e(TAG, "Error releasing", e) }
        interpreter = null
        modelInputElements  = NUM_FEATURES
        modelOutputElements = 1
    }

    fun extractLexicalFeatures(domain: String): LexicalFeatures {
        val lower     = domain.lowercase()
        val digits    = lower.count { it.isDigit() }
        val letters   = lower.count { it.isLetter() }
        val dots      = lower.count { it == '.' }
        val delimiters = lower.count { !it.isLetterOrDigit() && it != '.' }
        // F5: jumlah kata mencurigakan (COUNT, bukan boolean) — sesuai training Python
        val suspiciousWordCount = SUSPICIOUS_WORDS.count { lower.contains(it) }
        val digitToLetterRatio = if (letters > 0) digits.toFloat() / letters.toFloat() else digits.toFloat()
        // F7: panjang maksimal digit berurutan (COUNT, bukan boolean) — sesuai training Python
        val maxSequentialDigits = Regex("\\d+").findAll(lower).maxOfOrNull { it.value.length } ?: 0

        return LexicalFeatures(
            domainLength        = domain.length,
            numDigits           = digits,
            numDots             = dots,
            numDelimiters       = delimiters,
            suspiciousWordCount = suspiciousWordCount,
            digitToLetterRatio  = digitToLetterRatio,
            maxSequentialDigits = maxSequentialDigits
        )
    }

    /**
     * Buat ByteBuffer dengan ukuran yang TEPAT sesuai kebutuhan model.
     * - Jika model butuh 7 elemen: masukkan 7 fitur
     * - Jika model butuh lebih: pad dengan 0
     * - Jika model butuh kurang: hanya masukkan sejumlah yang diminta
     * Ini mencegah native crash akibat buffer size mismatch di TFLite.
     */
    private fun buildInputBuffer(features: LexicalFeatures, inputElements: Int): ByteBuffer {
        val featureArray = floatArrayOf(
            features.domainLength.toFloat(),
            features.numDigits.toFloat(),
            features.numDots.toFloat(),
            features.numDelimiters.toFloat(),
            features.suspiciousWordCount.toFloat(),  // F5: count, bukan boolean
            features.digitToLetterRatio,
            features.maxSequentialDigits.toFloat()   // F7: max length, bukan boolean
        )
        val buffer = ByteBuffer.allocateDirect(inputElements * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until inputElements) {
            buffer.putFloat(if (i < featureArray.size) featureArray[i] else 0.0f)
        }
        buffer.rewind()
        return buffer
    }

    private fun normalizeDomain(url: String): String {
        var d = url.lowercase().trim()
        d = d.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        d = d.split("/").first()
        d = d.split("?").first()
        d = d.split("#").first()
        d = d.split(":").first()
        return d
    }

    private fun isDomainValid(domain: String): Boolean {
        if (domain.contains(" ") || !domain.contains(".")) return false
        val parts = domain.split(".")
        if (parts.size < 2) return false
        val tld = parts.last().lowercase()
        if (tld.isEmpty() || tld.length > 6) return false
        if (parts.size >= 3 && "${parts[parts.size - 2]}.$tld" in SECOND_LEVEL_TLDS) return true
        return tld in COMMON_TLDS
    }

    private fun resolveModelPath(modelPath: String): String? {
        val file = File(modelPath)
        if (file.isAbsolute && file.exists()) return modelPath
        val fileInDir = File(context.filesDir, "models/$modelPath")
        if (fileInDir.exists()) return fileInDir.absolutePath
        return try { copyAssetToFile(modelPath) } catch (e: Exception) { null }
    }

    private fun copyAssetToFile(assetName: String): String {
        val file = File(context.filesDir, assetName)
        val assetSize = try { context.assets.open(assetName).use { it.available().toLong() } } catch (e: Exception) { -1L }
        if (!file.exists() || file.length() != assetSize) {
            file.parentFile?.mkdirs()
            context.assets.open(assetName).use { i -> FileOutputStream(file).use { o -> i.copyTo(o) } }
        }
        return file.absolutePath
    }
}
