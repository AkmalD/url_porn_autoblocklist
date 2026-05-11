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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.lawanpmo.autoblocklist.data.repository.BlocklistRepository
import com.lawanpmo.autoblocklist.presentation.ui.theme.Green500
import com.lawanpmo.autoblocklist.presentation.ui.theme.Orange500
import com.lawanpmo.autoblocklist.presentation.ui.theme.Purple500
import com.lawanpmo.autoblocklist.service.UrlBlockerAccessibilityService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@Composable
fun HomeScreen(
    blocklistRepository: BlocklistRepository? = null,
    onModelSelected: (MLModelType) -> Unit = {}
) {
    val context = LocalContext.current
    var isServiceEnabled by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(MLModelType.CNN_1D) }
    var blockedHistory by remember { mutableStateOf(emptyList<com.lawanpmo.autoblocklist.data.model.BlockedDomainRecord>()) }
    var blocklistStats by remember { mutableStateOf(com.lawanpmo.autoblocklist.data.model.BlocklistStatistics.empty()) }

    // Check service status on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = isAccessibilityServiceEnabled(context)
                
                // Refresh blocked history
                blocklistRepository?.let {
                    blockedHistory = it.getRecentBlockedDomains(10)
                    blocklistStats = it.getStatistics()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
            StatusCard(isServiceEnabled)

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
                    MaterialTheme.colorScheme.primary else Purple500
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Blocked Domains History & Calculated Statistics
            if (blocklistRepository != null) {
                BlockedDomainsHistoryCard(
                    stats = blocklistStats,
                    recentBlocked = blockedHistory
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

            Spacer(modifier = Modifier.height(32.dp))

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
                OutlinedButton(
                    onClick = {
                        openAccessibilitySettings(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Buka Pengaturan",
                        style = MaterialTheme.typography.titleMedium
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
    accentColor: Color
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
            // Header with Active Badge
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
                        
                        // Active Badge
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
                    
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Grid
            Column(modifier = Modifier.fillMaxWidth()) {
                // Accuracy
                StatRow(
                    label = "Akurasi",
                    value = "%.2f%%".format(model.accuracy),
                    icon = Icons.Default.Psychology
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Precision
                StatRow(
                    label = "Presisi",
                    value = "%.2f%%".format(model.precision),
                    icon = Icons.Default.Security
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Detection Time
                StatRow(
                    label = "Waktu Deteksi",
                    value = "${model.detectionTimeMs}ms",
                    icon = Icons.Default.Speed
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Model Size
                StatRow(
                    label = "Ukuran Model",
                    value = "${model.modelSizeKb}KB",
                    icon = Icons.Default.Lock
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
private fun BlockedDomainsHistoryCard(
    stats: com.lawanpmo.autoblocklist.data.model.BlocklistStatistics,
    recentBlocked: List<com.lawanpmo.autoblocklist.data.model.BlockedDomainRecord>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0)  // Light orange background
        ),
        border = BorderStroke(1.dp, Color(0xFFFFB74D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Text(
                text = "📊 Riwayat Deteksi Situs",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE65100)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Statistics
            Column(modifier = Modifier.fillMaxWidth()) {
                // Total Blocked
                BlockedHistoryStatRow(
                    label = "Total Terdeteksi",
                    value = stats.totalBlocked.toString(),
                    icon = Icons.Default.Warning
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Unique Domains
                BlockedHistoryStatRow(
                    label = "Domain Unik",
                    value = stats.blockedDomainsUnique.toString(),
                    icon = Icons.Default.Shield
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Avg Detection Time
                BlockedHistoryStatRow(
                    label = "Rata-rata Waktu Deteksi",
                    value = "%.1f ms".format(stats.avgDetectionTimeMs),
                    icon = Icons.Default.Speed
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Calculated Accuracy
                BlockedHistoryStatRow(
                    label = "Akurasi Terhitung",
                    value = "%.2f%%".format(stats.calculatedAccuracy),
                    icon = Icons.Default.CheckCircle
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Model Usage
                BlockedHistoryStatRow(
                    label = "Deteksi CNN-1D",
                    value = "${stats.cnn1dCount}x",
                    icon = Icons.Default.Psychology
                )

                Spacer(modifier = Modifier.height(8.dp))

                BlockedHistoryStatRow(
                    label = "Deteksi Random Forest",
                    value = "${stats.randomForestCount}x",
                    icon = Icons.Default.AutoAwesome
                )
            }

            if (recentBlocked.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Situs Terakhir Diblokir",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE65100)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Recent blocked domains
                recentBlocked.forEachIndexed { index, record ->
                    if (index < 5) {  // Show only first 5
                        RecentBlockedItem(record)
                        if (index < minOf(4, recentBlocked.size - 1)) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedHistoryStatRow(
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
                modifier = Modifier.size(18.dp),
                tint = Color(0xFFE65100)
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
            color = Color(0xFFD84315)
        )
    }
}

@Composable
private fun RecentBlockedItem(record: com.lawanpmo.autoblocklist.data.model.BlockedDomainRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.domain,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE65100)
            )
            Text(
                text = "${record.detectionTimeMs}ms • ${record.modelUsed}",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            text = "${"%.0f%%".format(record.score * 100)}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD84315),
            modifier = Modifier
                .background(Color(0xFFFFE0B2), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
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
private fun StatusCard(isServiceEnabled: Boolean) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isServiceEnabled) Green500.copy(alpha = 0.1f) else Orange500.copy(alpha = 0.1f),
        label = "status_bg"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isServiceEnabled) Green500 else Orange500,
        label = "status_icon"
    )

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
                imageVector = if (isServiceEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = iconColor
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isServiceEnabled) "Proteksi Aktif" else "Proteksi Tidak Aktif",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = iconColor
                )
                Text(
                    text = if (isServiceEnabled)
                        "Layanan sedang memantau browser Anda"
                    else
                        "Aktifkan layanan aksesibilitas untuk mulai",
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
