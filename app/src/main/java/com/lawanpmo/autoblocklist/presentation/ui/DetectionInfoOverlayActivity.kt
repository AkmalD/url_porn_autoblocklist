package com.lawanpmo.autoblocklist.presentation.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lawanpmo.autoblocklist.data.model.LexicalFeatures
import com.lawanpmo.autoblocklist.presentation.ui.theme.AutoBlocklistTheme
import kotlinx.coroutines.delay

class DetectionInfoOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_EXTRACTED_DOMAIN_NAME = "extracted_domain_name"
        const val EXTRA_SCORE = "score"
        const val EXTRA_IS_ADULT = "is_adult"
        const val EXTRA_MODEL = "model"
        const val EXTRA_INFERENCE_TIME_MS = "inference_time_ms"
        const val EXTRA_LEXICAL_FEATURES = "lexical_features"
        private const val AUTO_DISMISS_SECONDS = 15
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val domain = intent.getStringExtra(EXTRA_DOMAIN) ?: "-"
        val extractedDomainName = intent.getStringExtra(EXTRA_EXTRACTED_DOMAIN_NAME)
        val score = intent.getFloatExtra(EXTRA_SCORE, 0f)
        val isAdult = intent.getBooleanExtra(EXTRA_IS_ADULT, false)
        val model = intent.getStringExtra(EXTRA_MODEL) ?: "-"
        val inferenceTimeMs = intent.getDoubleExtra(EXTRA_INFERENCE_TIME_MS, 0.0)
        val lexicalFeatures: LexicalFeatures? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_LEXICAL_FEATURES, LexicalFeatures::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_LEXICAL_FEATURES)
        }

        val safeGreen = Color(0xFF2E7D32)
        val lightGreen = Color(0xFFE8F5E9)

        setContent {
            AutoBlocklistTheme {
                var countdown by remember { mutableIntStateOf(AUTO_DISMISS_SECONDS) }

                LaunchedEffect(Unit) {
                    for (i in AUTO_DISMISS_SECONDS downTo 1) {
                        countdown = i
                        delay(1000L)
                    }
                    finish()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isAdult) MaterialTheme.colorScheme.errorContainer else lightGreen
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = if (isAdult) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = if (isAdult) MaterialTheme.colorScheme.error else safeGreen
                        )

                        Text(
                            text = if (isAdult) "URL BERBAHAYA TERDETEKSI" else "URL AMAN TERDETEKSI",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            color = if (isAdult) MaterialTheme.colorScheme.error else safeGreen
                        )

                        // --- Kartu Info Klasifikasi ---
                        SectionCard(title = "Info Klasifikasi") {
                            InfoRow("Domain (URL)", domain)
                            Divider()
                            InfoRow(
                                label = "Input Model",
                                value = extractedDomainName ?: domain,
                                valueColor = if (extractedDomainName != null && extractedDomainName != domain)
                                    Color(0xFFE65100) else Color.Unspecified
                            )
                            Divider()
                            InfoRow("Skor", "${"%.4f".format(score)}  (${(score * 100).toInt()}%)")
                            Divider()
                            InfoRow("Model", model)
                            Divider()
                            InfoRow("Waktu Inferensi", "${"%.2f".format(inferenceTimeMs)} ms")
                            Divider()
                            InfoRow(
                                label = "Hasil",
                                value = if (isAdult) "BERBAHAYA" else "AMAN",
                                valueColor = if (isAdult) MaterialTheme.colorScheme.error else safeGreen
                            )
                        }

                        // --- Kartu Feature Extraction ---
                        if (lexicalFeatures != null) {
                            SectionCard(title = "Feature Extraction (Random Forest)") {
                                FeatureTableHeader()
                                Divider(thickness = 1.5.dp)
                                FeatureRow(
                                    code = "F1",
                                    name = "Panjang Domain",
                                    value = lexicalFeatures.domainLength.toString()
                                )
                                FeatureRow(
                                    code = "F2",
                                    name = "Jumlah Digit",
                                    value = lexicalFeatures.numDigits.toString()
                                )
                                FeatureRow(
                                    code = "F3",
                                    name = "Jumlah Titik",
                                    value = lexicalFeatures.numDots.toString()
                                )
                                FeatureRow(
                                    code = "F4",
                                    name = "Jumlah Delimiter",
                                    value = lexicalFeatures.numDelimiters.toString()
                                )
                                FeatureRow(
                                    code = "F5",
                                    name = "Jml Kata Mencurigakan",
                                    value = "${lexicalFeatures.suspiciousWordCount} kata",
                                    valueColor = if (lexicalFeatures.suspiciousWordCount > 0)
                                        Color(0xFFB71C1C) else Color(0xFF2E7D32)
                                )
                                FeatureRow(
                                    code = "F6",
                                    name = "Rasio Digit/Huruf",
                                    value = "%.3f".format(lexicalFeatures.digitToLetterRatio)
                                )
                                FeatureRow(
                                    code = "F7",
                                    name = "Max Digit Berurutan",
                                    value = "${lexicalFeatures.maxSequentialDigits} digit",
                                    valueColor = if (lexicalFeatures.maxSequentialDigits > 0)
                                        Color(0xFFB71C1C) else Color(0xFF2E7D32)
                                )
                                Divider(thickness = 1.5.dp)
                                // Vektor input akhir ke model
                                val vector = buildString {
                                    append("[")
                                    append(lexicalFeatures.domainLength)
                                    append(", ")
                                    append(lexicalFeatures.numDigits)
                                    append(", ")
                                    append(lexicalFeatures.numDots)
                                    append(", ")
                                    append(lexicalFeatures.numDelimiters)
                                    append(", ")
                                    append(lexicalFeatures.suspiciousWordCount)
                                    append(", ")
                                    append("%.3f".format(lexicalFeatures.digitToLetterRatio))
                                    append(", ")
                                    append(lexicalFeatures.maxSequentialDigits)
                                    append("]")
                                }
                                Text(
                                    text = "Vektor Input: $vector",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // --- Kartu Legend ---
                            SectionCard(title = "Keterangan Fitur") {
                                LegendRow("F1", "Panjang Domain", "Total karakter domain termasuk titik dan TLD")
                                LegendRow("F2", "Jumlah Digit", "Banyak karakter angka (0–9) dalam domain")
                                LegendRow("F3", "Jumlah Titik", "Banyak pemisah titik — menandai kedalaman subdomain")
                                LegendRow("F4", "Jumlah Delimiter", "Karakter bukan huruf/angka/titik, misal: - _")
                                LegendRow("F5", "Kata Mencurigakan", "1 jika mengandung: porn, sex, xxx, adult, bokep, hentai, tube, cam, dll")
                                LegendRow("F6", "Rasio Digit/Huruf", "numDigit ÷ numHuruf — domain acak/phishing cenderung tinggi")
                                LegendRow("F7", "Digit Berurutan ≥3", "1 jika ada pola angka berurutan 3+ digit, misal: 123, 4567")
                            }
                        } else {
                            // CNN-1D aktif — tidak ada feature extraction
                            SectionCard(title = "Feature Extraction") {
                                Text(
                                    text = "Feature extraction hanya tersedia untuk model Random Forest.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Model aktif saat ini: $model",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Ganti ke Random Forest di halaman utama aplikasi untuk melihat detail fitur.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Button(
                            onClick = { finish() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAdult) MaterialTheme.colorScheme.error else safeGreen
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(
                                text = "KEMBALI KE BROWSER ($countdown)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Divider()
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
            modifier = Modifier.weight(0.55f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun FeatureTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text("Kode", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.15f))
        Text("Nama Fitur", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
        Text("Nilai", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.35f), textAlign = TextAlign.End)
    }
}

@Composable
private fun FeatureRow(code: String, name: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.15f)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
            modifier = Modifier.weight(0.35f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun LegendRow(code: String, name: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = code,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 1.dp)
        )
        Column {
            Text(text = name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
