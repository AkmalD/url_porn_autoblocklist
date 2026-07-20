package com.lawanpmo.autoblocklist.presentation.ui.evaluation

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.lawanpmo.autoblocklist.data.model.SingleModelTestResult
import com.lawanpmo.autoblocklist.data.model.TestUrlItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Util untuk impor URL massal & ekspor hasil evaluasi ke CSV.
 *
 * Tujuan utama: memudahkan pengumpulan data untuk uji Wilcoxon waktu inferensi.
 * CSV memakai titik sebagai pemisah desimal (bukan koma) supaya langsung terbaca
 * pandas/scipy di Python tanpa konversi.
 */
object EvaluationExport {

    /**
     * Parse teks tempel massal menjadi daftar [TestUrlItem].
     *
     * Setiap baris: `url[,;<tab>spasi]label`
     * - label opsional. Dianggap ADULT bila bernilai: 1, adult, porn, true, ya
     * - label aman bila: 0, aman, safe, false, tidak — atau bila tidak ada label
     * Baris kosong & baris diawali `#` diabaikan. Protokol/www/slash dibersihkan.
     */
    fun parseBulk(text: String): List<TestUrlItem> {
        val adultTokens = setOf("1", "adult", "porn", "true", "ya", "yes", "y")
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                // pisahkan berdasarkan koma / titik-koma / tab / spasi ganda
                val parts = line.split(Regex("[,;\\t]|\\s{2,}"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val rawUrl = parts.getOrNull(0) ?: return@mapNotNull null
                val url = rawUrl
                    .removePrefix("https://").removePrefix("http://").removePrefix("www.")
                    .trimEnd('/')
                    .lowercase()
                if (url.isBlank()) return@mapNotNull null
                val labelToken = parts.getOrNull(1)?.lowercase()
                val isAdult = labelToken != null && labelToken in adultTokens
                TestUrlItem(url, isAdult)
            }
            .toList()
    }

    /** Susun isi CSV dari hasil evaluasi satu model: url, label, waktu inferensi. */
    fun buildCsv(results: List<SingleModelTestResult>, modelName: String): String {
        val sb = StringBuilder()
        sb.append("url,label,inference_time_ms\n")
        for (r in results) {
            sb.append(esc(r.url)).append(',')
            sb.append(if (r.groundTruth) "1" else "0").append(',')
            sb.append(fmt(r.inferenceTimeMs, 4))
            sb.append('\n')
        }
        return sb.toString()
    }

    /**
     * Tulis CSV ke folder app-specific (tanpa perlu izin storage di semua versi
     * Android) lalu kembalikan [File]. Lokasi: /Android/data/<pkg>/files/evaluasi/
     */
    fun writeCsv(context: Context, csv: String, modelName: String): File {
        val dir = File(context.getExternalFilesDir(null), "evaluasi").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeModel = modelName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, "eval_${safeModel}_$stamp.csv")
        file.writeText(csv)
        return file
    }

    /** Buka share sheet (kirim ke Drive/Gmail/file manager) untuk file CSV. */
    fun shareCsv(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, "Bagikan CSV hasil evaluasi")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun fmt(v: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", v)

    /** Escape nilai CSV bila mengandung koma/kutip/baris baru. */
    private fun esc(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"" + s.replace("\"", "\"\"") + "\""
        else s
}
