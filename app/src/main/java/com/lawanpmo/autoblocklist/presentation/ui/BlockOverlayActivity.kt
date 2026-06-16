package com.lawanpmo.autoblocklist.presentation.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lawanpmo.autoblocklist.data.model.LexicalFeatures
import com.lawanpmo.autoblocklist.presentation.ui.theme.AutoBlocklistTheme

class BlockOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCKED_DOMAIN = "blocked_domain"
        const val EXTRA_EXTRACTED_DOMAIN_NAME = "extracted_domain_name"
        const val EXTRA_SCORE = "score"
        const val EXTRA_MODEL = "model"
        const val EXTRA_INFERENCE_TIME_MS = "inference_time_ms"
        const val EXTRA_LEXICAL_FEATURES = "lexical_features"
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val blockedDomain = intent.getStringExtra(EXTRA_BLOCKED_DOMAIN) ?: "Unknown"
        val extractedDomainName = intent.getStringExtra(EXTRA_EXTRACTED_DOMAIN_NAME)
        val score = intent.getFloatExtra(EXTRA_SCORE, 0f)
        val model = intent.getStringExtra(EXTRA_MODEL) ?: "-"
        val inferenceTimeMs = intent.getDoubleExtra(EXTRA_INFERENCE_TIME_MS, 0.0)
        val lexicalFeatures: LexicalFeatures? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_LEXICAL_FEATURES, LexicalFeatures::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_LEXICAL_FEATURES)
        }

        setContent {
            AutoBlocklistTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.errorContainer
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
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )

                        Text(
                            text = "HAYOLOH MAU NGAPAIN?",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )

                        Text(
                            text = "Aplikasi ini diblokir oleh orang tua kamu.\nJangan nakal ya!",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )

                        // --- Kartu Info Klasifikasi ---
                        SectionCard(title = "Info Klasifikasi") {
                            InfoRow("Domain (URL)", blockedDomain)
                            Divider()
                            InfoRow(
                                label = "Input Model",
                                value = extractedDomainName ?: blockedDomain,
                                valueColor = if (extractedDomainName != null && extractedDomainName != blockedDomain)
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
                                value = "BERBAHAYA",
                                valueColor = MaterialTheme.colorScheme.error
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

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { goToHome() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text(
                                text = "KEMBALI KE JALAN YANG BENAR",
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
        goToHome()
    }

    private fun goToHome() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
}
