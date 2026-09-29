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
        private const val DEFAULT_THRESHOLD = 0.5f
        private const val NUM_FEATURES = 7
        private const val SUSPICIOUS_WORDS_ASSET = "rf/kata_sensitif_android.txt"

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

    // Dimuat SEKALI saat classifier diinisialisasi (bukan per-klasifikasi) agar
    // pencarian n-gram F5 tidak mengulang 10.000 entri di jalur yang diukur waktunya.
    private val suspiciousWords: Set<String> = loadSuspiciousWords()

    private fun loadSuspiciousWords(): Set<String> {
        return try {
            context.assets.open(SUSPICIOUS_WORDS_ASSET).bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toHashSet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memuat daftar kata sensitif dari $SUSPICIOUS_WORDS_ASSET", e)
            emptySet()
        }
    }

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
            val score = runModel(interp, features)

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

    private fun runModel(interp: Interpreter, features: LexicalFeatures): Float {
        val inputBuffer  = buildInputBuffer(features, modelInputElements)
        val outputBuffer = ByteBuffer.allocateDirect(modelOutputElements * 4).order(ByteOrder.nativeOrder())

        interp.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()
        return if (modelOutputElements >= 2) {
            outputBuffer.float  // skip p_safe
            outputBuffer.float  // p_adult
        } else {
            outputBuffer.float
        }
    }

    @Synchronized
    override fun classifyWithoutDomainGate(url: String): UrlClassificationResult {
        val interp = checkNotNull(interpreter) { "Model RF belum dimuat" }
        val startNs = System.nanoTime()
        val fullDomain = normalizeDomain(url)
        val features = extractLexicalFeatures(fullDomain)
        val score = runModel(interp, features)
        val inferenceTime = (System.nanoTime() - startNs) / 1_000_000.0
        return UrlClassificationResult(
            score = score,
            isAdult = score >= DEFAULT_THRESHOLD,
            inferenceTimeMs = inferenceTime,
            domain = fullDomain,
            lexicalFeatures = features
        )
    }

    /** Probabilitas porno tanpa gerbang isDomainValid() — untuk uji paritas. */
    fun scoreWithoutDomainGate(url: String): Float = classifyWithoutDomainGate(url).score

    override fun isModelLoaded(): Boolean = interpreter != null

    override fun release() {
        try { interpreter?.close() } catch (e: Exception) { Log.e(TAG, "Error releasing", e) }
        interpreter = null
        modelInputElements  = NUM_FEATURES
        modelOutputElements = 1
    }

    // F5 dihitung sebagai jumlah n-gram BERBEDA (panjang 2..6) dari domain yang ada
    // di daftar kata sensitif — irisan HashSet, bukan iterasi 10.000 entri + contains().
    fun extractLexicalFeatures(domain: String): LexicalFeatures =
        LexicalFeatureExtractor.extract(domain, suspiciousWords)

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
