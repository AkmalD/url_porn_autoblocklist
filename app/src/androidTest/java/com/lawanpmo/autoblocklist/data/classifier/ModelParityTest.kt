package com.lawanpmo.autoblocklist.data.classifier

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Uji paritas model di perangkat: skor RF & CNN-1D untuk 4.000 domain test.csv
 * dibandingkan dengan prediksi Colab (prediksi_test_*.csv).
 *
 * CSV acuan ada di app/src/androidTest/assets (APK test, bukan APK aplikasi).
 * Ringkasan & detail per domain ditulis ke
 * /sdcard/Android/data/com.lawanpmo.autoblocklist/files/paritas/.
 */
@RunWith(AndroidJUnit4::class)
class ModelParityTest {

    private data class Row(val domain: String, val label: Int, val prob: Double, val pred: Int)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val testContext: Context = instrumentation.context
    private val appContext: Context = instrumentation.targetContext

    @Test
    fun randomForestMatchesColab() {
        val rf = RandomForestClassifier(appContext)
        assertTrue("Gagal memuat model RF", runBlocking { rf.loadModel("rf/url_classifier_rf.tflite") })
        try {
            checkParity("rf", "prediksi_test_rf.csv", 0.7450, rf::scoreWithoutDomainGate) {
                rf.classify(it).skipped
            }
        } finally {
            rf.release()
        }
    }

    @Test
    fun cnn1dMatchesColab() {
        val cnn = UrlClassifier(appContext)
        assertTrue("Gagal memuat model CNN-1D", runBlocking { cnn.loadModel("cnn/CNN1D_url_classifier.tflite") })
        try {
            checkParity("cnn1d", "prediksi_test_cnn1d.csv", 0.7612, cnn::scoreWithoutDomainGate) {
                cnn.classify(it).skipped
            }
        } finally {
            cnn.release()
        }
    }

    private fun checkParity(
        name: String,
        colabCsv: String,
        expectedAccuracy: Double,
        score: (String) -> Float,
        skippedByClassify: (String) -> Boolean
    ) {
        val testDomains = readCsv("test.csv") { c -> c[0] to c[1].toInt() }
        val colab = readCsv(colabCsv) { c -> Row(c[0], c[1].toInt(), c[2].toDouble(), c[3].toInt()) }
        assertEquals("Jumlah baris $colabCsv", testDomains.size, colab.size)

        val detail = StringBuilder("domain,label,prob_colab,prob_device,pred_colab,pred_device\n")
        var predMatches = 0
        var correctDevice = 0
        var correctColab = 0
        var maxProbDiff = 0.0
        var maxDiffDomain = ""
        var skipped = 0

        for ((i, row) in colab.withIndex()) {
            val (domain, label) = testDomains[i]
            assertEquals("Urutan domain baris $i", domain, row.domain)

            val prob = score(domain).toDouble()
            // Ambang sama dengan Colab: pred = prob >= 0.5
            val pred = if (prob >= 0.5) 1 else 0
            val diff = Math.abs(prob - row.prob)

            if (pred == row.pred) predMatches++
            if (pred == label) correctDevice++
            if (row.pred == label) correctColab++
            if (diff > maxProbDiff) { maxProbDiff = diff; maxDiffDomain = domain }
            if (skippedByClassify(domain)) skipped++

            detail.append(domain).append(',').append(label).append(',')
                .append(row.prob).append(',').append(prob).append(',')
                .append(row.pred).append(',').append(pred).append('\n')
        }

        val n = colab.size
        val matchRate = predMatches.toDouble() / n
        val accDevice = correctDevice.toDouble() / n
        val accColab = correctColab.toDouble() / n
        val summary = String.format(
            Locale.US,
            "[%s] prediksi cocok %d/%d (%.4f%%) | akurasi perangkat %.5f (Colab %.5f, target %.4f) | " +
                "selisih prob maks %.3e (%s) | dilewati gerbang isDomainValid di classify(): %d/%d",
            name, predMatches, n, matchRate * 100, accDevice, accColab, expectedAccuracy,
            maxProbDiff, maxDiffDomain, skipped, n
        )
        Log.i(TAG, summary)
        writeReport(name, summary, detail)

        assertTrue("$summary\nKecocokan prediksi < 99,9%", matchRate >= 0.999)
        assertEquals("$summary\nAkurasi perangkat != akurasi Colab", correctColab, correctDevice)
        assertEquals("$summary\nAkurasi perangkat != target", expectedAccuracy, accDevice, 1e-4)
    }

    private fun <T> readCsv(asset: String, parse: (List<String>) -> T): List<T> =
        testContext.assets.open(asset).bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { parse(it.trim().split(",")) }.toList()
        }

    private fun writeReport(name: String, summary: String, detail: CharSequence) {
        val dir = File(appContext.getExternalFilesDir(null), "paritas").apply { mkdirs() }
        File(dir, "paritas_$name.csv").writeText(detail.toString())
        File(dir, "ringkasan_$name.txt").writeText(summary + "\n")
    }

    companion object {
        private const val TAG = "ModelParityTest"
    }
}
