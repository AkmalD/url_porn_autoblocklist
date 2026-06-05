package com.lawanpmo.autoblocklist.data.classifier

import android.content.Context
import android.util.Log
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

/**
 * URL Classifier using TensorFlow Lite.
 *
 * Uses ML-only classification with domain name extraction:
 * 1. Extract main domain name from URL (e.g., play.google.com → google)
 * 2. Run CNN 1D classification on domain name
 * 3. Return adult probability score
 *
 * NO HARDCODED WHITELIST - relies on domain name extraction for accuracy.
 */
@Singleton
class UrlClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) : IUrlClassifier {

    companion object {
        private const val TAG = "UrlClassifier"
        private const val MAX_LEN = 34
        private const val DEFAULT_THRESHOLD = 0.7f

        private val CHAR_TO_IDX: Map<Char, Int> = buildMap {
            put('\u0000', 0)  // PAD
            for ((i, c) in ('a'..'z').withIndex()) put(c, i + 2)
            for ((i, c) in ('0'..'9').withIndex()) put(c, i + 28)
            put('.', 38)
            put('-', 39)
            put('_', 40)
        }

        private const val PAD_IDX = 0
        private const val UNK_IDX = 1

        private val COMMON_TLDS = setOf(
            "com", "net", "org", "info", "biz", "name", "pro", "int",
            "co", "io", "me", "tv", "cc", "ws", "in", "ru", "cn", "jp", "kr",
            "de", "uk", "fr", "it", "es", "br", "au", "ca", "nl", "be", "ch",
            "at", "pl", "se", "no", "dk", "fi", "cz", "hu", "ro", "bg", "gr",
            "pt", "ie", "nz", "za", "sg", "hk", "tw", "my", "th", "ph", "id", "vn",
            "xyz", "top", "site", "online", "club", "live", "fun", "space",
            "tech", "store", "shop", "app", "dev", "cloud", "digital", "media",
            "news", "blog", "video", "games", "world", "network", "global",
            "center", "zone", "today", "one", "life", "work", "money", "email",
            "link", "click", "download", "stream", "watch", "porn", "sex",
            "xxx", "adult", "cam", "tube", "how",
            "tk", "ml", "ga", "cf", "gq",
            "ltd", "vip", "pw", "asia", "mobi", "tel", "travel", "jobs", "edu", "gov", "mil"
        )

        private val SECOND_LEVEL_TLDS = setOf(
            "co.id", "co.uk", "co.jp", "co.kr", "co.nz", "co.za", "co.in", "co.th",
            "com.au", "com.br", "com.cn", "com.hk", "com.my", "com.sg", "com.tw",
            "com.vn", "com.ph", "com.ar", "com.mx", "com.co", "com.pe", "com.ve",
            "com.ec", "com.pk", "com.bd", "com.ng", "com.eg", "com.tr", "com.ua", "com.ru",
            "net.au", "net.br", "net.cn", "net.id", "net.in", "net.nz", "net.za",
            "org.au", "org.br", "org.cn", "org.id", "org.in", "org.nz", "org.uk", "org.za",
            "ac.id", "ac.uk", "ac.jp", "ac.kr", "ac.nz", "ac.za", "ac.th",
            "edu.au", "edu.br", "edu.cn", "edu.hk", "edu.my", "edu.sg", "edu.tw", "edu.vn",
            "go.id", "go.jp", "go.kr", "go.th",
            "or.id", "or.jp", "or.kr", "or.th",
            "ne.jp", "ne.kr",
            "web.id", "sch.id", "my.id", "biz.id"
        )
    }

    private var interpreter: Interpreter? = null
    private var threshold: Float = DEFAULT_THRESHOLD

    override suspend fun loadModel(modelPath: String): Boolean {
        release()

        return try {
            Log.d(TAG, "🧠 Loading CNN-1D classifier model: $modelPath")

            val actualPath = resolveModelPath(modelPath) ?: return false

            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(File(actualPath), options)

            val inputTensor = interpreter?.getInputTensor(0)
            val outputTensor = interpreter?.getOutputTensor(0)

            Log.i(TAG, "✅ CNN-1D model loaded successfully")
            Log.d(TAG, "   Accuracy: 96.69% | Precision: 99.20% | Detection: 45ms")
            Log.d(TAG, "   Input shape: ${inputTensor?.shape()?.contentToString()}")
            Log.d(TAG, "   Output shape: ${outputTensor?.shape()?.contentToString()}")

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load CNN-1D classifier model", e)
            false
        }
    }

    @Synchronized
    override fun classify(url: String): UrlClassificationResult {
        val interp = interpreter
        if (interp == null) {
            Log.w(TAG, "Model not loaded")
            return UrlClassificationResult.error(url)
        }

        val startTime = System.currentTimeMillis()

        return try {
            val fullDomain = normalizeDomain(url)
            
            // Validate if input is a real domain (not a search query)
            if (!isDomainValid(fullDomain)) {
                Log.d(TAG, "⏭️  Skipped '$fullDomain' - not a valid domain (search query?)")
                return UrlClassificationResult(
                    score = 0.0f,
                    isAdult = false,
                    inferenceTimeMs = System.currentTimeMillis() - startTime,
                    domain = fullDomain,
                    skipped = true
                )
            }
            
            val domainName = extractMainDomainName(fullDomain)

            Log.d(TAG, "Domain extraction: '$fullDomain' → '$domainName'")

            val tokens = tokenize(domainName)
            val inputBuffer = prepareInputBuffer(tokens)
            val outputBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())

            interp.run(inputBuffer, outputBuffer)

            outputBuffer.rewind()
            val score = outputBuffer.float
            val inferenceTime = System.currentTimeMillis() - startTime
            val isAdult = score > threshold

            Log.d(TAG, "Classified '$fullDomain' (name='$domainName'): score=${"%.4f".format(score)}, isAdult=$isAdult, time=${inferenceTime}ms")

            UrlClassificationResult(
                score = score,
                isAdult = isAdult,
                inferenceTimeMs = inferenceTime,
                domain = fullDomain
            )
        } catch (e: Exception) {
            Log.e(TAG, "Classification error for: $url", e)
            UrlClassificationResult.error(normalizeDomain(url))
        }
    }

    /**
     * Validate if input is a real domain (not a search query)
     * Valid domains must:
     * 1. Have at least one dot (.) - e.g., example.com
     * 2. Have a valid TLD - .com, .org, .xxx, etc.
     * 3. Not be a search query (spaces, multiple words, etc.)
     */
    private fun isDomainValid(domain: String): Boolean {
        // No spaces allowed - search queries typically have spaces
        if (domain.contains(" ")) {
            Log.d(TAG, "   ❌ Invalid: contains spaces (likely search query)")
            return false
        }

        // Must have at least one dot
        if (!domain.contains(".")) {
            Log.d(TAG, "   ❌ Invalid: no dot found (likely search term, not domain)")
            return false
        }

        val parts = domain.split(".")
        
        // Domain should have at least 2 parts (name.tld)
        if (parts.size < 2) {
            Log.d(TAG, "   ❌ Invalid: domain has less than 2 parts")
            return false
        }

        // Check if last part (TLD) is valid
        val tld = parts.last().lowercase()
        if (tld.isEmpty() || tld.length > 6) {
            Log.d(TAG, "   ❌ Invalid: TLD invalid length ($tld)")
            return false
        }

        // Check if it has a valid TLD
        if (parts.size >= 3) {
            // Check second-level TLDs first (e.g., co.uk, com.au)
            val potentialSecondLevelTld = "${parts[parts.size - 2]}.${tld}"
            if (potentialSecondLevelTld in SECOND_LEVEL_TLDS) {
                Log.d(TAG, "   ✅ Valid: second-level TLD ($potentialSecondLevelTld)")
                return true
            }
        }

        // Check single-level TLDs
        if (tld in COMMON_TLDS) {
            Log.d(TAG, "   ✅ Valid: TLD found ($tld)")
            return true
        }

        Log.d(TAG, "   ❌ Invalid: TLD not recognized ($tld)")
        return false
    }

    private fun normalizeDomain(url: String): String {
        var domain = url.lowercase().trim()
        domain = domain.removePrefix("https://")
        domain = domain.removePrefix("http://")
        domain = domain.removePrefix("www.")
        domain = domain.split("/").firstOrNull() ?: domain
        domain = domain.split("?").firstOrNull() ?: domain
        domain = domain.split("#").firstOrNull() ?: domain
        domain = domain.split(":").firstOrNull() ?: domain
        return domain
    }

    private fun extractMainDomainName(fullDomain: String): String {
        val parts = fullDomain.split(".")

        if (parts.size < 2) return fullDomain

        if (parts.size >= 3) {
            val potentialSecondLevelTld = "${parts[parts.size - 2]}.${parts.last()}"
            if (potentialSecondLevelTld in SECOND_LEVEL_TLDS) {
                return parts[parts.size - 3]
            }
        }

        if (parts.last() in COMMON_TLDS) {
            return parts[parts.size - 2]
        }

        return if (parts.size >= 2) parts[parts.size - 2] else fullDomain
    }

    private fun tokenize(domain: String): IntArray {
        val tokens = IntArray(MAX_LEN) { PAD_IDX }
        for ((i, char) in domain.take(MAX_LEN).withIndex()) {
            tokens[i] = CHAR_TO_IDX[char] ?: UNK_IDX
        }
        return tokens
    }

    private fun prepareInputBuffer(tokens: IntArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(MAX_LEN * 4)
        buffer.order(ByteOrder.nativeOrder())
        for (token in tokens) {
            buffer.putFloat(token.toFloat())
        }
        buffer.rewind()
        return buffer
    }

    override fun isModelLoaded(): Boolean = interpreter != null

    override fun release() {
        try {
            interpreter?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing interpreter", e)
        }
        interpreter = null
    }

    private fun resolveModelPath(modelPath: String): String? {
        val file = File(modelPath)
        if (file.isAbsolute && file.exists()) return modelPath

        val fileInDir = File(context.filesDir, "models/$modelPath")
        if (fileInDir.exists()) return fileInDir.absolutePath

        return try {
            copyAssetToFile(modelPath)
        } catch (e: Exception) {
            Log.e(TAG, "Could not resolve model path: $modelPath", e)
            null
        }
    }

    private fun copyAssetToFile(assetName: String): String {
        val file = File(context.filesDir, assetName)

        val assetSize = try {
            context.assets.open(assetName).use { it.available().toLong() }
        } catch (e: Exception) {
            -1L
        }

        val needsCopy = !file.exists() || file.length() != assetSize

        if (needsCopy) {
            Log.d(TAG, "Copying model from assets")
            file.parentFile?.mkdirs()
            context.assets.open(assetName).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }

        return file.absolutePath
    }
}
