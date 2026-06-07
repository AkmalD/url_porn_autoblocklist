package com.lawanpmo.autoblocklist.presentation.ui.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lawanpmo.autoblocklist.data.model.MLModelType
import com.lawanpmo.autoblocklist.data.model.MLModels
import com.lawanpmo.autoblocklist.data.preference.ModelPreference
import com.lawanpmo.autoblocklist.data.repository.BlocklistRepository
import com.lawanpmo.autoblocklist.presentation.ui.theme.Green500
import com.lawanpmo.autoblocklist.presentation.ui.theme.Orange500
import com.lawanpmo.autoblocklist.presentation.ui.theme.Purple500
import com.lawanpmo.autoblocklist.service.UrlBlockerAccessibilityService

@Composable
fun HomeScreen(
    blocklistRepository: BlocklistRepository? = null,
    modelPreference: ModelPreference? = null,
    onModelSelected: (MLModelType) -> Unit = {},
    onNavigateToEvaluation: () -> Unit = {}
) {
    val context = LocalContext.current
    var isServiceEnabled by remember { mutableStateOf(false) }
    var isDetectionEnabled by remember { mutableStateOf(modelPreference?.isDetectionEnabled() ?: true) }
    var selectedModel by remember { mutableStateOf(MLModelType.CNN_1D) }
    var blockedHistory by remember { mutableStateOf(emptyList<com.lawanpmo.autoblocklist.data.model.BlockedDomainRecord>()) }
    var blocklistStats by remember { mutableStateOf(com.lawanpmo.autoblocklist.data.model.BlocklistStatistics.empty()) }
    var showHistoryModal by remember { mutableStateOf(false) }

    // Check service status on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = isAccessibilityServiceEnabled(context)
                isDetectionEnabled = modelPreference?.isDetectionEnabled() ?: true

                // Refresh blocked history
                blocklistRepository?.let {
                    blockedHistory = it.getAllBlockedDomains()
                    blocklistStats = it.getStatisticsByModel(selectedModel.name)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Update statistics when selected model changes
    LaunchedEffect(selectedModel) {
        blocklistRepository?.let {
            blocklistStats = it.getStatisticsByModel(selectedModel.name)
        }
    }

    // Show history modal when opened
    if (showHistoryModal && blocklistRepository != null) {
        HistoryModal(
            allBlockedRecords = blockedHistory.filter { 
                it.modelUsed.contains(selectedModel.name, ignoreCase = true) 
            },
            onDismiss = { showHistoryModal = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header with gradient
        HeaderSection(isServiceEnabled)

        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Card
            StatusCard(isServiceEnabled, isDetectionEnabled)

            Spacer(modifier = Modifier.height(24.dp))

            // Model Selection Section
            Text(
                text = "Pilih Model Deteksi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Model Selector Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModelSelectorCard(
                    model = MLModels.CNN_1D,
                    isSelected = selectedModel == MLModelType.CNN_1D,
                    onClick = {
                        selectedModel = MLModelType.CNN_1D
                        onModelSelected(MLModelType.CNN_1D)
                    },
                    modifier = Modifier.weight(1f),
                    accentColor = MaterialTheme.colorScheme.primary
                )

                ModelSelectorCard(
                    model = MLModels.RANDOM_FOREST,
                    isSelected = selectedModel == MLModelType.RANDOM_FOREST,
                    onClick = {
                        selectedModel = MLModelType.RANDOM_FOREST
                        onModelSelected(MLModelType.RANDOM_FOREST)
                    },
                    modifier = Modifier.weight(1f),
                    accentColor = Purple500
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Model Statistics
            ModelStatsCard(
                model = MLModels.getByType(selectedModel),
                accentColor = if (selectedModel == MLModelType.CNN_1D) 
                    MaterialTheme.colorScheme.primary else Purple500,
                isServiceEnabled = isServiceEnabled,
                accuracy = blocklistStats.calculatedAccuracy,
                avgDetectionTimeMs = blocklistStats.avgDetectionTimeMs
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Blocked Domains History Button
            if (blocklistRepository != null && blocklistStats.totalBlocked > 0) {
                HistoryButtonCard(
                    uniqueDomainCount = blocklistStats.blockedDomainsUnique,
                    totalDetections = blocklistStats.totalBlocked,
                    selectedModelName = selectedModel.name,
                    onClick = { showHistoryModal = true }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Features
            Text(
                text = "Fitur Utama",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeatureCard(
                icon = Icons.Default.Psychology,
                title = "Deteksi Machine Learning",
                description = "Mendeteksi pola nama domain berbahaya secara otomatis dengan akurasi tinggi"
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeatureCard(
                icon = Icons.Default.Speed,
                title = "Real-time Protection",
                description = "Memblokir situs sebelum konten dimuat, bukan setelah halaman terbuka"
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeatureCard(
                icon = Icons.Default.Lock,
                title = "100% Offline & Privasi",
                description = "Semua proses deteksi berjalan di perangkat, tidak ada data yang dikirim ke server"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Evaluasi Button
            OutlinedButton(
                onClick = onNavigateToEvaluation,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Purple500)
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Purple500
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Evaluasi Perbandingan Model",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Purple500
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button
            if (!isServiceEnabled) {
                Button(
                    onClick = {
                        openAccessibilitySettings(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Aktifkan Proteksi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Guide text for Samsung users
                Text(
                    text = "Pilih \"Installed apps\" > \"URL AutoBlocklist\" > Aktifkan",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Start/Stop Detection Toggle
                if (isDetectionEnabled) {
                    Button(
                        onClick = {
                            modelPreference?.setDetectionEnabled(false)
                            isDetectionEnabled = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Stop Deteksi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            modelPreference?.setDetectionEnabled(true)
                            isDetectionEnabled = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Green500
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Start Deteksi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        openAccessibilitySettings(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Buka Pengaturan Aksesibilitas",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer info
            Text(
                text = "Aplikasi ini adalah bagian dari penelitian Tugas Akhir tentang deteksi konten dewasa berbasis URL menggunakan Machine Learning",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModelSelectorCard(
    model: com.lawanpmo.autoblocklist.data.model.MLModelStats,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, accentColor)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (model.type == MLModelType.CNN_1D) 
                        Icons.Default.Psychology else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = accentColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = model.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${model.accuracy.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModelStatsCard(
    model: com.lawanpmo.autoblocklist.data.model.MLModelStats,
    accentColor: Color,
    isServiceEnabled: Boolean,
    accuracy: Double,
    avgDetectionTimeMs: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.08f)
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, accentColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header with Active Badge (conditional)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Statistik Model",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                        
                        // Active Badge - Only show if service is enabled
                        if (isServiceEnabled) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        accentColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✓ AKTIF",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }
                        }
                    }
                    
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Grid - Show only dynamic data
            Column(modifier = Modifier.fillMaxWidth()) {
                // Calculated Accuracy from average score
                StatRow(
                    label = "Akurasi",
                    value = "%.1f%%".format(accuracy),
                    icon = Icons.Default.Psychology
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Average Detection Time
                StatRow(
                    label = "Rata-rata Waktu Deteksi",
                    value = "%.1f ms".format(avgDetectionTimeMs),
                    icon = Icons.Default.Speed
                )
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HistoryButtonCard(
    uniqueDomainCount: Int,
    totalDetections: Int,
    selectedModelName: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "📊 Riwayat Deteksi Situs ($selectedModelName)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF9800)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "$uniqueDomainCount domain unik • $totalDetections deteksi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color(0xFFFF9800)
            )
        }
    }
}

@Composable
private fun HistoryModal(
    allBlockedRecords: List<com.lawanpmo.autoblocklist.data.model.BlockedDomainRecord>,
    onDismiss: () -> Unit
) {
    // Get unique domains
    val uniqueDomains = allBlockedRecords.map { it.domain }.distinct()
    val modelName = allBlockedRecords.firstOrNull()?.modelUsed ?: "Unknown"
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800)
                )
            ) {
                Text("Tutup")
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Riwayat $modelName",
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onDismiss),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Summary
                Text(
                    text = "Total Situs Diblokir: ${uniqueDomains.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF9800),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Divider()
                
                // Scrollable list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allBlockedRecords) { record ->
                        HistoryRecordItem(record)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun HistoryRecordItem(record: com.lawanpmo.autoblocklist.data.model.BlockedDomainRecord) {
    var showFeatures by remember { mutableStateOf(false) }
    val hasFeatures = record.lexicalFeatures != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        ),
        border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = record.domain,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFF9800)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${record.detectionTimeMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = record.modelUsed,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF9800),
                    modifier = Modifier
                        .background(Color(0xFFFF9800).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )

                Text(
                    text = "${"%.0f%%".format(record.score * 100)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color(0xFFFF9800), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Tombol expand fitur leksikal (hanya untuk RF)
            if (hasFeatures) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFeatures = !showFeatures },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (showFeatures) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Purple500
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showFeatures) "Sembunyikan Fitur Leksikal" else "Lihat Fitur Leksikal",
                        style = MaterialTheme.typography.labelSmall,
                        color = Purple500
                    )
                }

                if (showFeatures) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LexicalFeaturesPanel(features = record.lexicalFeatures!!)
                }
            }
        }
    }
}

@Composable
private fun LexicalFeaturesPanel(features: com.lawanpmo.autoblocklist.data.model.LexicalFeatures) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Purple500.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(
            text = "Hasil Ekstraksi Fitur Leksikal",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Purple500,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 2-column grid
        val featureRows = listOf(
            Pair("1. Panjang Domain", "${features.domainLength}"),
            Pair("2. Jumlah Angka", "${features.numDigits}"),
            Pair("3. Jumlah Titik", "${features.numDots}"),
            Pair("4. Jumlah Delimiter", "${features.numDelimiters}"),
            Pair("5. Kata Mencurigakan", "${features.suspiciousWordCount} kata"),
            Pair("6. Rasio Angka/Huruf", "%.3f".format(features.digitToLetterRatio)),
            Pair("7. Max Digit Berurutan", "${features.maxSequentialDigits} digit")
        )

        featureRows.chunked(2).forEach { rowPair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPair.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (label.contains("Mencurigakan") || label.contains("Berurutan"))
                                (if (value == "Ada") Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface)
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                // Padding untuk row yang hanya punya 1 item
                if (rowPair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun BlockedDomainsHistoryCard(
    stats: com.lawanpmo.autoblocklist.data.model.BlocklistStatistics,
    recentBlocked: List<com.lawanpmo.autoblocklist.data.model.BlockedDomainRecord>
) {
    // This composable is kept for backward compatibility but not used
    // The functionality is moved to HistoryModal and HistoryButtonCard
}

@Composable
private fun HeaderSection(isServiceEnabled: Boolean) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isServiceEnabled) Green500 else MaterialTheme.colorScheme.primary,
        label = "header_color"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor,
                        backgroundColor.copy(alpha = 0.8f)
                    )
                )
            )
            .padding(top = 48.dp, bottom = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "URL AutoBlocklist",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Proteksi Otomatis dari Konten Dewasa",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun StatusCard(isServiceEnabled: Boolean, isDetectionEnabled: Boolean = true) {
    val statusColor = when {
        !isServiceEnabled -> Orange500
        isDetectionEnabled -> Green500
        else -> Color(0xFFFF8F00)
    }

    val backgroundColor by animateColorAsState(
        targetValue = statusColor.copy(alpha = 0.1f),
        label = "status_bg"
    )
    val iconColor by animateColorAsState(
        targetValue = statusColor,
        label = "status_icon"
    )

    val statusTitle = when {
        !isServiceEnabled -> "Proteksi Tidak Aktif"
        isDetectionEnabled -> "Proteksi Aktif"
        else -> "Deteksi Dijeda"
    }

    val statusDesc = when {
        !isServiceEnabled -> "Aktifkan layanan aksesibilitas untuk mulai"
        isDetectionEnabled -> "Layanan sedang memantau browser Anda"
        else -> "Tekan Start Deteksi untuk melanjutkan"
    }

    val statusIcon = when {
        isServiceEnabled && isDetectionEnabled -> Icons.Default.CheckCircle
        isServiceEnabled && !isDetectionEnabled -> Icons.Default.Stop
        else -> Icons.Default.Warning
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = iconColor
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = iconColor
                )
                Text(
                    text = statusDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val serviceName = "${context.packageName}/${UrlBlockerAccessibilityService::class.java.canonicalName}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    return enabledServices.split(':').any { it.equals(serviceName, ignoreCase = true) }
}

private fun openAccessibilitySettings(context: Context) {
    // Open accessibility settings
    // Note: Samsung OneUI doesn't support opening specific service detail page programmatically
    // User needs to tap: "Installed apps" > "URL AutoBlocklist"
    try {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (e: Exception) {
        // Ignore
    }
}
