package com.lawanpmo.autoblocklist.presentation.ui.evaluation

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lawanpmo.autoblocklist.data.classifier.RandomForestClassifier
import com.lawanpmo.autoblocklist.data.classifier.UrlClassifier
import com.lawanpmo.autoblocklist.data.model.*
import com.lawanpmo.autoblocklist.presentation.ui.theme.Green500
import com.lawanpmo.autoblocklist.presentation.ui.theme.Purple500
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluationScreen(
    cnnClassifier: UrlClassifier,
    rfClassifier: RandomForestClassifier,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var testUrls by remember { mutableStateOf(DEFAULT_TEST_URLS.toMutableList()) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UrlTestResult>?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val cnnMetrics = results?.let { ModelEvaluationMetrics.fromResults(it, useCnn = true) }
    val rfMetrics = results?.let { ModelEvaluationMetrics.fromResults(it, useCnn = false) }

    if (showAddDialog) {
        AddUrlDialog(
            onAdd = { url, isAdult ->
                testUrls = (testUrls + TestUrlItem(url, isAdult)).toMutableList()
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Evaluasi Model",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "CNN-1D vs Random Forest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // --- Dataset Section ---
            item {
                SectionHeader(
                    title = "Dataset Uji",
                    badge = "${testUrls.size} URL • ${testUrls.count { it.isAdult }} Adult • ${testUrls.count { !it.isAdult }} Aman"
                )
            }

            itemsIndexed(testUrls) { index, item ->
                TestUrlRow(
                    item = item,
                    onDelete = {
                        testUrls = testUrls.toMutableList().also { it.removeAt(index) }
                        results = null
                    },
                    onToggleLabel = { newIsAdult ->
                        testUrls = testUrls.toMutableList().also {
                            it[index] = item.copy(isAdult = newIsAdult)
                        }
                        results = null
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tambah URL")
                    }
                    OutlinedButton(
                        onClick = {
                            testUrls = DEFAULT_TEST_URLS.toMutableList()
                            results = null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
            }

            // --- Evaluasi Button ---
            item {
                if (isLoading) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Text(
                                text = loadingMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            if (testUrls.isEmpty()) return@Button
                            scope.launch {
                                // launch{} berjalan di Main thread — aman untuk update UI state
                                isLoading = true
                                errorMessage = null
                                try {
                                    // Update UI (Main thread) → pindah ke IO → kembali ke Main
                                    loadingMessage = "Memuat model CNN-1D..."
                                    withContext(Dispatchers.IO) {
                                        if (!cnnClassifier.isModelLoaded()) {
                                            cnnClassifier.loadModel("url_classifier.tflite")
                                        }
                                    }

                                    loadingMessage = "Memuat model Random Forest..."
                                    withContext(Dispatchers.IO) {
                                        if (!rfClassifier.isModelLoaded()) {
                                            rfClassifier.loadModel("url_classifier_rf.tflite")
                                        }
                                    }

                                    // Pastikan kedua model berhasil dimuat
                                    if (!cnnClassifier.isModelLoaded()) {
                                        errorMessage = "Gagal memuat model CNN-1D. Pastikan file url_classifier.tflite ada di assets."
                                        isLoading = false
                                        return@launch
                                    }
                                    if (!rfClassifier.isModelLoaded()) {
                                        errorMessage = "Gagal memuat model Random Forest. Pastikan file url_classifier_rf.tflite ada di assets."
                                        isLoading = false
                                        return@launch
                                    }

                                    val snapshot = testUrls.toList()
                                    loadingMessage = "Menjalankan evaluasi pada ${snapshot.size} URL..."

                                    // Semua inferensi dijalankan di IO thread, TANPA menyentuh UI state
                                    val evalResults: List<UrlTestResult> = withContext(Dispatchers.IO) {
                                        snapshot.map { item ->
                                            val cnn = try {
                                                cnnClassifier.classify(item.url)
                                            } catch (e: Throwable) {
                                                Log.e("Evaluation", "CNN error on ${item.url}", e)
                                                com.lawanpmo.autoblocklist.domain.repository.UrlClassificationResult.error(item.url)
                                            }
                                            val rf = try {
                                                rfClassifier.classify(item.url)
                                            } catch (e: Throwable) {
                                                Log.e("Evaluation", "RF error on ${item.url}", e)
                                                com.lawanpmo.autoblocklist.domain.repository.UrlClassificationResult.error(item.url)
                                            }
                                            UrlTestResult(
                                                url = item.url,
                                                groundTruth = item.isAdult,
                                                cnnPrediction = cnn.isAdult,
                                                cnnScore = cnn.score,
                                                cnnTimeMs = cnn.inferenceTimeMs,
                                                rfPrediction = rf.isAdult,
                                                rfScore = rf.score,
                                                rfTimeMs = rf.inferenceTimeMs,
                                                rfLexicalFeatures = rf.lexicalFeatures
                                            )
                                        }
                                    }

                                    // Kembali ke Main thread — aman update UI state
                                    results = evalResults
                                    loadingMessage = ""
                                } catch (e: Throwable) {
                                    errorMessage = "Error: ${e.javaClass.simpleName} — ${e.message}"
                                    Log.e("Evaluation", "Evaluation failed", e)
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = testUrls.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Mulai Evaluasi",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFFE53935),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // --- Hasil Evaluasi ---
            if (results != null && cnnMetrics != null && rfMetrics != null) {
                item {
                    Divider()
                    Spacer(Modifier.height(4.dp))
                    SectionHeader(
                        title = "Hasil Evaluasi",
                        badge = "${results!!.size} URL diuji"
                    )
                }

                // Metrics Cards side by side
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ModelMetricsCard(
                            modelName = "CNN-1D",
                            metrics = cnnMetrics,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        ModelMetricsCard(
                            modelName = "Random Forest",
                            metrics = rfMetrics,
                            accentColor = Purple500,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Confusion Matrix
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ConfusionMatrixCard(
                            modelName = "CNN-1D",
                            metrics = cnnMetrics,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        ConfusionMatrixCard(
                            modelName = "Random Forest",
                            metrics = rfMetrics,
                            accentColor = Purple500,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Inference Time Comparison
                item {
                    InferenceTimeCard(cnnMetrics = cnnMetrics, rfMetrics = rfMetrics)
                }

                // Detail per URL
                item {
                    SectionHeader(title = "Detail Hasil per URL", badge = null)
                }

                item {
                    // Table header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Domain", modifier = Modifier.weight(2.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Label", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("CNN", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("RF", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }

                itemsIndexed(results!!) { index, result ->
                    UrlResultRow(result = result, isEven = index % 2 == 0)
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// ─── Sub-composables ────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, badge: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun TestUrlRow(
    item: TestUrlItem,
    onDelete: () -> Unit,
    onToggleLabel: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.url,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))

        // Toggle label
        val labelColor = if (item.isAdult) Color(0xFFE53935) else Green500
        val labelText = if (item.isAdult) "ADULT" else "AMAN"
        Box(
            modifier = Modifier
                .background(labelColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                .border(1.dp, labelColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .clickable { onToggleLabel(!item.isAdult) }
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = labelColor
            )
        }

        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Hapus",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModelMetricsCard(
    modelName: String,
    metrics: ModelEvaluationMetrics,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = modelName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Divider(color = accentColor.copy(alpha = 0.3f))
            MetricRow("Accuracy", "%.1f%%".format(metrics.accuracy * 100), accentColor)
            MetricRow("Precision", "%.1f%%".format(metrics.precision * 100), accentColor)
            MetricRow("Recall", "%.1f%%".format(metrics.recall * 100), accentColor)
            MetricRow("F1-Score", "%.1f%%".format(metrics.f1Score * 100), accentColor)
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accentColor)
    }
}

@Composable
private fun ConfusionMatrixCard(
    modelName: String,
    metrics: ModelEvaluationMetrics,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Confusion Matrix $modelName",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )

            // Header row
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1.2f))
                Text("Pred +", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Pred -", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // TP / FN row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Act +", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MatrixCell(value = metrics.truePositives, color = Green500, modifier = Modifier.weight(1f))
                MatrixCell(value = metrics.falseNegatives, color = Color(0xFFE53935), modifier = Modifier.weight(1f))
            }

            // FP / TN row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Act -", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MatrixCell(value = metrics.falsePositives, color = Color(0xFFE53935), modifier = Modifier.weight(1f))
                MatrixCell(value = metrics.trueNegatives, color = Green500, modifier = Modifier.weight(1f))
            }

            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LegendDot(color = Green500, label = "Benar")
                LegendDot(color = Color(0xFFE53935), label = "Salah")
            }
        }
    }
}

@Composable
private fun MatrixCell(value: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1.2f)
            .padding(2.dp)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InferenceTimeCard(
    cnnMetrics: ModelEvaluationMetrics,
    rfMetrics: ModelEvaluationMetrics
) {
    val cnnColor = MaterialTheme.colorScheme.primary
    val rfColor = Purple500
    val maxTime = maxOf(cnnMetrics.avgInferenceTimeMs, rfMetrics.avgInferenceTimeMs).toFloat().coerceAtLeast(1f)
    val faster = if (cnnMetrics.avgInferenceTimeMs <= rfMetrics.avgInferenceTimeMs) "CNN-1D" else "Random Forest"
    val fasterColor = if (cnnMetrics.avgInferenceTimeMs <= rfMetrics.avgInferenceTimeMs) cnnColor else rfColor

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Perbandingan Inference Time",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            // CNN bar
            InferenceBar(
                modelName = "CNN-1D",
                avgMs = cnnMetrics.avgInferenceTimeMs,
                totalMs = cnnMetrics.totalInferenceTimeMs,
                fraction = (cnnMetrics.avgInferenceTimeMs / maxTime).toFloat(),
                color = cnnColor
            )

            // RF bar
            InferenceBar(
                modelName = "Random Forest",
                avgMs = rfMetrics.avgInferenceTimeMs,
                totalMs = rfMetrics.totalInferenceTimeMs,
                fraction = (rfMetrics.avgInferenceTimeMs / maxTime).toFloat(),
                color = rfColor
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(fasterColor.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = fasterColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$faster lebih cepat (${"%.1f".format(
                        if (faster == "CNN-1D") rfMetrics.avgInferenceTimeMs - cnnMetrics.avgInferenceTimeMs
                        else cnnMetrics.avgInferenceTimeMs - rfMetrics.avgInferenceTimeMs
                    )} ms)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = fasterColor
                )
            }
        }
    }
}

@Composable
private fun InferenceBar(
    modelName: String,
    avgMs: Double,
    totalMs: Long,
    fraction: Float,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(modelName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color)
            Text(
                text = "avg ${"%.1f".format(avgMs)} ms | total ${totalMs} ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(6.dp))
            )
        }
    }
}

@Composable
private fun UrlResultRow(result: UrlTestResult, isEven: Boolean) {
    var showFeatures by remember { mutableStateOf(false) }
    val rowBg = if (isEven) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Domain + expand toggle
            Column(modifier = Modifier.weight(2.5f)) {
                Text(
                    text = result.url,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (result.rfLexicalFeatures != null) {
                    Text(
                        text = if (showFeatures) "▲ sembunyikan fitur" else "▼ lihat fitur RF",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Purple500,
                        modifier = Modifier.clickable { showFeatures = !showFeatures }
                    )
                }
            }
            LabelBadge(isAdult = result.groundTruth, modifier = Modifier.weight(1.2f))
            PredictionBadge(
                predicted = result.cnnPrediction, correct = result.cnnCorrect,
                score = result.cnnScore, timeMs = result.cnnTimeMs,
                modifier = Modifier.weight(1.5f)
            )
            PredictionBadge(
                predicted = result.rfPrediction, correct = result.rfCorrect,
                score = result.rfScore, timeMs = result.rfTimeMs,
                modifier = Modifier.weight(1.5f)
            )
        }

        // Panel fitur leksikal RF — tampil untuk SEMUA URL (aman maupun adult)
        if (showFeatures && result.rfLexicalFeatures != null) {
            EvalLexicalFeaturesPanel(
                features = result.rfLexicalFeatures,
                isPornographic = result.groundTruth,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun EvalLexicalFeaturesPanel(
    features: com.lawanpmo.autoblocklist.data.model.LexicalFeatures,
    isPornographic: Boolean,
    modifier: Modifier = Modifier
) {
    val labelColor = if (isPornographic) Color(0xFFE53935) else Green500
    val labelText  = if (isPornographic) "PORNOGRAFI" else "AMAN"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Purple500.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
            .border(1.dp, Purple500.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Fitur Leksikal RF",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Purple500
            )
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = labelColor,
                modifier = Modifier
                    .background(labelColor.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }

        val rows = listOf(
            Triple("1. Panjang Domain",      features.domainLength.toString(),                         false),
            Triple("2. Jumlah Angka",         features.numDigits.toString(),                            false),
            Triple("3. Jumlah Titik",         features.numDots.toString(),                              false),
            Triple("4. Jumlah Delimiter",     features.numDelimiters.toString(),                        false),
            Triple("5. Kata Mencurigakan",    if (features.hasSuspiciousWords) "Ada" else "Tidak",      features.hasSuspiciousWords),
            Triple("6. Rasio Angka/Huruf",   "%.3f".format(features.digitToLetterRatio),               false),
            Triple("7. Angka Berurutan",      if (features.hasSequentialDigits) "Ada" else "Tidak",     features.hasSequentialDigits)
        )

        rows.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { (label, value, isRed) ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRed && value == "Ada") Color(0xFFE53935)
                                    else MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LabelBadge(isAdult: Boolean, modifier: Modifier = Modifier) {
    val color = if (isAdult) Color(0xFFE53935) else Green500
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = if (isAdult) "Adult" else "Aman",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PredictionBadge(
    predicted: Boolean,
    correct: Boolean,
    score: Float,
    timeMs: Long,
    modifier: Modifier = Modifier
) {
    val bgColor = if (correct) Green500.copy(alpha = 0.12f) else Color(0xFFE53935).copy(alpha = 0.12f)
    val textColor = if (correct) Green500 else Color(0xFFE53935)

    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor, RoundedCornerShape(3.dp))
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (predicted) "Adult" else "Aman",
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = "${"%.0f".format(score * 100)}% · ${timeMs}ms",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddUrlDialog(
    onAdd: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    var isAdult by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah URL Uji") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it.trim() },
                    label = { Text("Domain (contoh: google.com)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Text("Label Ground Truth:", style = MaterialTheme.typography.bodySmall)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isAdult,
                        onClick = { isAdult = false },
                        label = { Text("Aman") },
                        leadingIcon = if (!isAdult) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Green500.copy(alpha = 0.2f),
                            selectedLabelColor = Green500
                        )
                    )
                    FilterChip(
                        selected = isAdult,
                        onClick = { isAdult = true },
                        label = { Text("Adult") },
                        leadingIcon = if (isAdult) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE53935).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFFE53935)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleaned = urlText
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .removePrefix("www.")
                        .trimEnd('/')
                    if (cleaned.isNotBlank()) onAdd(cleaned, isAdult)
                },
                enabled = urlText.isNotBlank()
            ) {
                Text("Tambah")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
