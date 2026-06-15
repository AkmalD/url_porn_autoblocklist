package com.lawanpmo.autoblocklist.presentation.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lawanpmo.autoblocklist.data.classifier.RandomForestClassifier
import com.lawanpmo.autoblocklist.data.classifier.UrlClassifier
import com.lawanpmo.autoblocklist.data.model.MLModelType
import com.lawanpmo.autoblocklist.data.preference.ModelPreference
import com.lawanpmo.autoblocklist.data.repository.BlocklistRepository
import com.lawanpmo.autoblocklist.presentation.ui.evaluation.EvaluationScreen
import com.lawanpmo.autoblocklist.presentation.ui.home.HomeScreen
import com.lawanpmo.autoblocklist.presentation.ui.theme.AutoBlocklistTheme
import com.lawanpmo.autoblocklist.service.UrlBlockerAccessibilityService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var modelPreference: ModelPreference
    @Inject lateinit var blocklistRepository: BlocklistRepository
    @Inject lateinit var cnnClassifier: UrlClassifier
    @Inject lateinit var rfClassifier: RandomForestClassifier

    companion object {
        private const val TAG = "MainActivity"
    }

    private var showEvaluation by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AutoBlocklistTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showEvaluation) {
                        EvaluationScreen(
                            onBack = { showEvaluation = false }
                        )
                    } else {
                        HomeScreen(
                            blocklistRepository = blocklistRepository,
                            modelPreference = modelPreference,
                            onModelSelected = { modelType, fileName -> handleModelSelection(modelType, fileName) },
                            onNavigateToEvaluation = { showEvaluation = true }
                        )
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        if (showEvaluation) {
            showEvaluation = false
        } else {
            super.onBackPressed()
        }
    }

    private fun handleModelSelection(modelType: MLModelType, fileName: String) {
        Log.i(TAG, "User selected model: ${modelType.name}/$fileName")
        modelPreference.setSelectedModel(modelType)
        modelPreference.setSelectedModelFile(modelType, fileName)
        switchModelInService(modelType, fileName)
    }

    private fun switchModelInService(modelType: MLModelType, fileName: String) {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val service = getRunningAccessibilityService()
                if (service != null) {
                    val success = service.switchClassificationModel(modelType, fileName)
                    Log.i(TAG, if (success) "✅ Model switched in service" else "⚠️ Model switch failed")
                } else {
                    Log.d(TAG, "Service not running — preference saved, will apply on next start")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error switching model in service", e)
            }
        }
    }

    private fun getRunningAccessibilityService(): UrlBlockerAccessibilityService? {
        return try { null } catch (e: Exception) { null }
    }
}
