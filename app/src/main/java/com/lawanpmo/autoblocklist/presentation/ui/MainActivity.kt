package com.lawanpmo.autoblocklist.presentation.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.lawanpmo.autoblocklist.data.model.MLModelType
import com.lawanpmo.autoblocklist.data.preference.ModelPreference
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

    @Inject
    lateinit var modelPreference: ModelPreference

    @Inject
    lateinit var blocklistRepository: com.lawanpmo.autoblocklist.data.repository.BlocklistRepository

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            AutoBlocklistTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(
                        blocklistRepository = blocklistRepository,
                        onModelSelected = { modelType ->
                            handleModelSelection(modelType)
                        }
                    )
                }
            }
        }
    }

    /**
     * Handle model selection from HomeScreen
     */
    private fun handleModelSelection(modelType: MLModelType) {
        Log.i(TAG, "User selected model: ${modelType.name}")
        
        // Save preference
        modelPreference.setSelectedModel(modelType)
        Log.i(TAG, "Saved model preference: ${modelType.name}")
        
        // Try to switch model in accessibility service
        switchModelInService(modelType)
    }

    /**
     * Switch model in running accessibility service
     */
    private fun switchModelInService(modelType: MLModelType) {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val service = getRunningAccessibilityService()
                if (service != null) {
                    val success = service.switchClassificationModel(modelType)
                    if (success) {
                        Log.i(TAG, "✅ Model switched in service")
                    } else {
                        Log.w(TAG, "⚠️ Model switch failed in service")
                    }
                } else {
                    Log.d(TAG, "Accessibility service not running - model will be loaded when service starts")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error switching model in service", e)
            }
        }
    }

    /**
     * Get reference to running accessibility service
     * This is a workaround - normally you'd use other IPC methods
     */
    private fun getRunningAccessibilityService(): UrlBlockerAccessibilityService? {
        // Note: This is a simplified approach
        // In production, use bound services or other proper IPC mechanisms
        return try {
            // AccessibilityService cannot be directly retrieved
            // The service will automatically pick up the saved preference on next classification
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting accessibility service", e)
            null
        }
    }
}
