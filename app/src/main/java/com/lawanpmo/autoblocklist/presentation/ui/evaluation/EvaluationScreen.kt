package com.lawanpmo.autoblocklist.presentation.ui.evaluation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
fun EvaluationScreen(onBack: () -> Unit) {

    val context      = LocalContext.current.applicationContext
    val primaryColor = MaterialTheme.colorScheme.primary
    val scope        = rememberCoroutineScope()

    var selectedModel by remember { mutableStateOf(EvalModelChoice.CNN_1D) }
    val testUrls      = remember { mutableStateListOf(*DEFAULT_TEST_URLS.toTypedArray()) }
    var results       by remember { mutableStateOf<List<SingleModelTestResult>?>(null) }
    var evaluatedWith by remember { mutableStateOf<EvalModelChoice?>(null) }
    var isRunning     by remember { mutableStateOf(false) }
    var errorMessage  by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val metrics = results?.let { ModelEvaluationMetrics.fromResults(it) }

    // Blokir tombol back saat evaluasi berjalan agar tidak memicu release()
    // sementara TFLite masih berjalan di IO thread
    BackHandler(enabled = isRunning) { /* abaikan back saat running */ }

    if (showAddDialog) {
        AddUrlDialog(
            onAdd = { url, isAdult ->
                testUrls.add(TestUrlItem(url, isAdult))
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
                            text       = "Evaluasi Model",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text  = "Uji model TFLite dengan dataset URL",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (!isRunning) onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Pilih Model ──────────────────────────────────────────────────
            Column {
                SectionHeader("Pilih Model", null)
                Spacer(Modifier.height(8.dp))
                ModelSelector(
                    selected = selectedModel,
                    enabled  = !isRunning,
                    onSelect = {
                        selectedModel = it
                        results       = null
                        evaluatedWith = null
                        errorMessage  = null
                    }
                )
            }

            // ── Dataset ──────────────────────────────────────────────────────
            SectionHeader(
                title = "Dataset Uji",
                badge = "${testUrls.size} URL  •  ${testUrls.count { it.isAdult }} Adult  •  ${testUrls.count { !it.isAdult }} Aman"
            )

            testUrls.forEachIndexed { index, item ->
                TestUrlRow(
                    item          = item,
                    enabled       = !isRunning,
                    onDelete      = {
                        testUrls.removeAt(index)
                        results = null
                    },
                    onToggleLabel = { newIsAdult ->
                        testUrls[index] = item.copy(isAdult = newIsAdult)
                        results = null
                    }
                )
            }

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick  = { showAddDialog = true },
                    enabled  = !isRunning,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah URL")
                }
                OutlinedButton(
                    onClick  = {
                        testUrls.clear()
                        testUrls.addAll(DEFAULT_TEST_URLS)
                        results      = null
                        errorMessage = null
                    },
                    enabled  = !isRunning,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset")
                }
            }

            // ── Tombol Evaluasi ───────────────────────────────────────────────
            Button(
                onClick  = {
                    if (isRunning || testUrls.isEmpty()) return@Button
                    val chosen     = selectedModel
                    val snapshot   = testUrls.toList()
                    val modelPaths = when (chosen) {
                        EvalModelChoice.CNN_1D        -> listOf("url_classifier.tflite", "CNN1D.tflite")
                        EvalModelChoice.RANDOM_FOREST -> listOf("url_classifier_rf.tflite", "RandomForest.tflite")
                    }

                    // Instance dibuat fresh per-evaluasi dan TIDAK disimpan di remember.
                    // release() dipanggil di finally setelah semua inferensi selesai,
                    // sehingga tidak ada race condition antara release() dan classify().
                    scope.launch {
                        isRunning    = true
                        errorMessage = null
                        results      = null

                        val classifier = when (chosen) {
                            EvalModelChoice.CNN_1D        -> UrlClassifier(context)
                            EvalModelChoice.RANDOM_FOREST -> RandomForestClassifier(context)
                        }

                        try {
                            val loaded = withContext(Dispatchers.IO) {
                                modelPaths.any { path -> classifier.loadModel(path) }
                            }
                            if (!loaded) {
                                errorMessage = "Gagal memuat model. Pastikan file .tflite ada di assets."
                                return@launch
                            }

                            val res = withContext(Dispatchers.IO) {
                                snapshot.map { item ->
                                    val r = classifier.classify(item.url)
                                    SingleModelTestResult(
                                        url             = item.url,
                                        groundTruth     = item.isAdult,
                                        prediction      = r.isAdult,
                                        score           = r.score,
                                        inferenceTimeMs = r.inferenceTimeMs,
                                        lexicalFeatures = r.lexicalFeatures
                                    )
                                }
                            }

                            results       = res
                            evaluatedWith = chosen
                        } catch (e: Throwable) {
                            errorMessage = "Error: ${e.message ?: e::class.simpleName ?: "tidak diketahui"}"
                        } finally {
                            // Selalu release setelah inferensi selesai, tanpa ada
                            // thread lain yang bisa mengakses instance ini
                            try { classifier.release() } catch (_: Exception) {}
                            isRunning = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                enabled  = !isRunning && testUrls.isNotEmpty(),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text       = "Mengevaluasi...",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text       = "Mulai Evaluasi",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── Error ─────────────────────────────────────────────────────────
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier              = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error, null,
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text  = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // ── Hasil Evaluasi ────────────────────────────────────────────────
            if (results != null && metrics != null && evaluatedWith != null) {
                val modelLabel  = if (evaluatedWith == EvalModelChoice.CNN_1D) "CNN-1D" else "Random Forest"
                val accentColor = if (evaluatedWith == EvalModelChoice.CNN_1D) primaryColor else Purple500

                Column {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    SectionHeader(
                        title = "Hasil Evaluasi — $modelLabel",
                        badge = "${results!!.size} URL"
                    )
                }

                ModelMetricsCard(modelLabel, metrics, accentColor)

                ConfusionMatrixCard(modelLabel, metrics, accentColor)

                LatencyCard(metrics, modelLabel, accentColor)

                SectionHeader("Detail per URL", null)

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
                    Text("Domain",   Modifier.weight(2.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Label",    Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Prediksi", Modifier.weight(1.8f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }

                results!!.forEachIndexed { index, result ->
                    UrlResultRow(
                        result      = result,
                        isEven      = index % 2 == 0,
                        showLexical = evaluatedWith == EvalModelChoice.RANDOM_FOREST
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun ModelSelector(
    selected: EvalModelChoice,
    enabled: Boolean,
    onSelect: (EvalModelChoice) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ModelSelectorCard(
            label       = "CNN-1D",
            subtitle    = "Berbasis karakter domain",
            icon        = Icons.Default.Psychology,
            selected    = selected == EvalModelChoice.CNN_1D,
            enabled     = enabled,
            accentColor = primaryColor,
            modifier    = Modifier.weight(1f),
            onClick     = { onSelect(EvalModelChoice.CNN_1D) }
        )
        ModelSelectorCard(
            label       = "Random Forest",
            subtitle    = "7 fitur leksikal",
            icon        = Icons.Default.AccountTree,
            selected    = selected == EvalModelChoice.RANDOM_FOREST,
            enabled     = enabled,
            accentColor = Purple500,
            modifier    = Modifier.weight(1f),
            onClick     = { onSelect(EvalModelChoice.RANDOM_FOREST) }
        )
    }
}

@Composable
private fun ModelSelectorCard(
    label: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val effectiveAccent = if (enabled) accentColor else accentColor.copy(alpha = 0.4f)
    Card(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape    = RoundedCornerShape(10.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (selected) effectiveAccent.copy(alpha = 0.10f)
                             else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) effectiveAccent else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier              = Modifier.padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint     = if (selected) effectiveAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text       = label,
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color      = if (selected) effectiveAccent else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, badge: String?) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (badge != null) {
            Text(
                text     = badge,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun TestUrlRow(
    item: TestUrlItem,
    enabled: Boolean,
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
            text     = item.url,
            style    = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        val labelColor = if (item.isAdult) Color(0xFFE53935) else Green500
        Box(
            modifier = Modifier
                .background(labelColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                .border(1.dp, labelColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .clickable(enabled = enabled) { onToggleLabel(!item.isAdult) }
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = if (item.isAdult) "ADULT" else "AMAN",
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = labelColor
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick  = onDelete,
            enabled  = enabled,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(Icons.Default.Close, "Hapus", modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModelMetricsCard(
    modelName: String,
    metrics: ModelEvaluationMetrics,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.07f)),
        border   = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier            = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text       = "Metrik Evaluasi — $modelName",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color      = accentColor
            )
            HorizontalDivider(color = accentColor.copy(alpha = 0.25f))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricBox("Accuracy",  "%.1f%%".format(metrics.accuracy  * 100), accentColor, Modifier.weight(1f))
                MetricBox("Precision", "%.1f%%".format(metrics.precision * 100), accentColor, Modifier.weight(1f))
                MetricBox("Recall",    "%.1f%%".format(metrics.recall    * 100), accentColor, Modifier.weight(1f))
                MetricBox("F1-Score",  "%.1f%%".format(metrics.f1Score   * 100), accentColor, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, accentColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier
            .background(accentColor.copy(alpha = 0.09f), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accentColor)
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ConfusionMatrixCard(
    modelName: String,
    metrics: ModelEvaluationMetrics,
    accentColor: Color
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier            = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text       = "Confusion Matrix — $modelName",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color      = accentColor
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1.5f))
                Text("Prediksi +", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Prediksi −", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Aktual +", Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MatrixCell(metrics.truePositives,  Green500,          Modifier.weight(1f))
                MatrixCell(metrics.falseNegatives, Color(0xFFE53935), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Aktual −", Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MatrixCell(metrics.falsePositives, Color(0xFFE53935), Modifier.weight(1f))
                MatrixCell(metrics.trueNegatives,  Green500,          Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(Green500,          "TP / TN — benar")
                LegendDot(Color(0xFFE53935), "FP / FN — salah")
            }
        }
    }
}

@Composable
private fun MatrixCell(value: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1.5f)
            .padding(3.dp)
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = value.toString(),
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color      = color
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LatencyCard(
    metrics: ModelEvaluationMetrics,
    modelName: String,
    accentColor: Color
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier            = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text       = "Latensi Inferensi — $modelName",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LatencyBox("Rata-rata / URL",  "${"%.3f".format(metrics.avgInferenceTimeMs)} ms",  accentColor, Modifier.weight(1f))
                LatencyBox("Total semua URL",  "${"%.2f".format(metrics.totalInferenceTimeMs)} ms", accentColor, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LatencyBox(label: String, value: String, accentColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier
            .background(accentColor.copy(alpha = 0.09f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accentColor)
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun UrlResultRow(
    result: SingleModelTestResult,
    isEven: Boolean,
    showLexical: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val rowBg = if (isEven) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Column(modifier = Modifier.fillMaxWidth().background(rowBg)) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(2.5f)) {
                Text(
                    text     = result.url,
                    style    = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showLexical && result.lexicalFeatures != null) {
                    Text(
                        text     = if (expanded) "▲ sembunyikan fitur" else "▼ lihat fitur RF",
                        style    = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color    = Purple500,
                        modifier = Modifier.clickable { expanded = !expanded }
                    )
                }
            }
            LabelBadge(result.groundTruth, Modifier.weight(1.2f))
            PredictionBadge(result.prediction, result.correct, result.score, result.inferenceTimeMs, Modifier.weight(1.8f))
        }
        if (expanded && showLexical && result.lexicalFeatures != null) {
            LexicalFeaturesPanel(
                features = result.lexicalFeatures,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun LabelBadge(isAdult: Boolean, modifier: Modifier = Modifier) {
    val color = if (isAdult) Color(0xFFE53935) else Green500
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text       = if (isAdult) "Adult" else "Aman",
            style      = MaterialTheme.typography.labelSmall,
            color      = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PredictionBadge(
    predicted: Boolean,
    correct: Boolean,
    score: Float,
    timeMs: Double,
    modifier: Modifier = Modifier
) {
    val bgColor   = if (correct) Green500.copy(alpha = 0.12f) else Color(0xFFE53935).copy(alpha = 0.12f)
    val textColor = if (correct) Green500 else Color(0xFFE53935)
    Column(
        modifier            = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .background(bgColor, RoundedCornerShape(3.dp))
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = if (predicted) "Adult" else "Aman",
                style      = MaterialTheme.typography.labelSmall,
                color      = textColor,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text      = "${"%.0f".format(score * 100)}% · ${"%.2f".format(timeMs)}ms",
            style     = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LexicalFeaturesPanel(
    features: LexicalFeatures,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Purple500.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
            .border(1.dp, Purple500.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("Fitur Leksikal RF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Purple500)
        val rows = listOf(
            "1. Panjang Domain"    to features.domainLength.toString(),
            "2. Jumlah Angka"      to features.numDigits.toString(),
            "3. Jumlah Titik"      to features.numDots.toString(),
            "4. Jumlah Delimiter"  to features.numDelimiters.toString(),
            "5. Kata Mencurigakan" to "${features.suspiciousWordCount} kata",
            "6. Rasio Angka/Huruf" to "%.3f".format(features.digitToLetterRatio),
            "7. Max Digit Berurut" to "${features.maxSequentialDigits} digit"
        )
        rows.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (label, value) ->
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), fontWeight = FontWeight.SemiBold)
                    }
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
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
                    value         = urlText,
                    onValueChange = { urlText = it.trim() },
                    label         = { Text("Domain (contoh: google.com)") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(8.dp)
                )
                Text("Label Ground Truth:", style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isAdult,
                        onClick  = { isAdult = false },
                        label    = { Text("Aman") },
                        leadingIcon = if (!isAdult) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Green500.copy(alpha = 0.2f),
                            selectedLabelColor     = Green500
                        )
                    )
                    FilterChip(
                        selected = isAdult,
                        onClick  = { isAdult = true },
                        label    = { Text("Adult") },
                        leadingIcon = if (isAdult) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE53935).copy(alpha = 0.2f),
                            selectedLabelColor     = Color(0xFFE53935)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleaned = urlText
                        .removePrefix("https://").removePrefix("http://").removePrefix("www.")
                        .trimEnd('/')
                    if (cleaned.isNotBlank()) onAdd(cleaned, isAdult)
                },
                enabled = urlText.isNotBlank()
            ) { Text("Tambah") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
