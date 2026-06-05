package com.lawanpmo.autoblocklist.data.preference

import android.content.Context
import android.content.SharedPreferences
import com.lawanpmo.autoblocklist.data.model.MLModelType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences wrapper untuk manage model preference
 */
@Singleton
class ModelPreference @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREF_NAME = "model_preference"
        private const val KEY_MODEL_TYPE = "selected_model_type"
        private const val KEY_DETECTION_ENABLED = "detection_enabled"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * Get selected model type, default to CNN_1D
     */
    fun getSelectedModel(): MLModelType {
        val modelString = prefs.getString(KEY_MODEL_TYPE, MLModelType.CNN_1D.name) ?: MLModelType.CNN_1D.name
        return try {
            MLModelType.valueOf(modelString)
        } catch (e: Exception) {
            MLModelType.CNN_1D
        }
    }

    /**
     * Save selected model type
     */
    fun setSelectedModel(modelType: MLModelType) {
        prefs.edit().putString(KEY_MODEL_TYPE, modelType.name).apply()
    }

    fun isDetectionEnabled(): Boolean {
        return prefs.getBoolean(KEY_DETECTION_ENABLED, true)
    }

    fun setDetectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DETECTION_ENABLED, enabled).apply()
    }
}
