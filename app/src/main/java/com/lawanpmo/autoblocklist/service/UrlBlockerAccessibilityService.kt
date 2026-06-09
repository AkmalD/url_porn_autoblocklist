package com.lawanpmo.autoblocklist.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.lawanpmo.autoblocklist.data.classifier.ClassifierManager
import com.lawanpmo.autoblocklist.data.model.BlockedDomainRecord
import com.lawanpmo.autoblocklist.data.model.LexicalFeatures
import com.lawanpmo.autoblocklist.data.model.MLModelType
import com.lawanpmo.autoblocklist.data.preference.ModelPreference
import com.lawanpmo.autoblocklist.data.repository.BlocklistRepository
import com.lawanpmo.autoblocklist.presentation.ui.BlockOverlayActivity
import com.lawanpmo.autoblocklist.presentation.ui.DetectionInfoOverlayActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Accessibility Service for detecting and blocking adult URLs in browsers.
 *
 * Detection flow:
 * 1. Monitor browser URL bar for changes
 * 2. Extract domain name from URL
 * 3. Run ML classification with current model
 * 4. Block if adult content detected (score > 0.7)
 */
@AndroidEntryPoint
class UrlBlockerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UrlBlockerService"
        private const val URL_CHECK_THROTTLE_MS = 300L
        private const val BLOCK_DELAY_MS = 1000L
        private const val CONTINUOUS_MONITOR_INTERVAL_MS = 1000L // Check every 1 second while in browser
    }

    // ML Classifier Manager - injected via Hilt
    @Inject
    lateinit var classifierManager: ClassifierManager

    // Model Preference - injected via Hilt
    @Inject
    lateinit var modelPreference: ModelPreference

    // Blocklist Repository - injected via Hilt
    @Inject
    lateinit var blocklistRepository: BlocklistRepository

    // Coroutine scope for async operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // State
    private var isClassifierReady = false
    private var isDetectionEnabled = true
    private var currentActiveModel: MLModelType = MLModelType.CNN_1D
    private var preferenceCheckJob: Job? = null

    // Supported browsers
    private val browserPackages = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.beta",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "org.bromite.bromite",
        "com.duckduckgo.mobile.android"
    )

    // Full URL bar IDs for different browsers (package:id/view_id format)
    // These target the actual URL bar, not autocomplete suggestions
    // Note: Chrome uses both url_bar and location_bar depending on version
    private val addressBarIds = listOf(
        // Chrome variants - try multiple IDs
        "com.android.chrome:id/url_bar",
        "com.android.chrome:id/location_bar",
        "com.android.chrome:id/omnibox_text_field",
        "com.chrome.beta:id/url_bar",
        "com.chrome.beta:id/location_bar",
        "com.chrome.dev:id/url_bar",
        "com.chrome.dev:id/location_bar",
        "com.chrome.canary:id/url_bar",
        "com.chrome.canary:id/location_bar",
        // Samsung Internet
        "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.sec.android.app.sbrowser.beta:id/location_bar_edit_text",
        // Firefox
        "org.mozilla.firefox:id/url_bar_title",
        "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "org.mozilla.firefox_beta:id/url_bar_title",
        // Edge
        "com.microsoft.emmx:id/url_bar",
        "com.microsoft.emmx:id/location_bar",
        // Brave
        "com.brave.browser:id/url_bar",
        "com.brave.browser:id/location_bar",
        // Opera
        "com.opera.browser:id/url_field",
        "com.opera.mini.native:id/url_field",
        // Others
        "com.vivaldi.browser:id/url_bar",
        "com.kiwibrowser.browser:id/url_bar",
        "com.duckduckgo.mobile.android:id/omnibarTextInput"
    )

    // System UI packages to ignore
    private val systemUiPackages = setOf(
        "com.samsung.android.honeyboard",
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
        "com.swiftkey.swiftkey",
        "com.android.systemui",
        "com.samsung.android.app.cocktailbarservice",
        "com.sec.android.inputmethod"
    )

    // State
    private var currentBrowserPackage: String? = null
    private var lastDetectedUrl: String? = null
    private var lastUrlCheckTime = 0L
    private var pendingBlockJob: Job? = null

    // Cache for URL bar ID (optimization - reduces AccessibilityNodeInfo allocations)
    private var cachedUrlBarId: String? = null
    private var cachedUrlBarPackage: String? = null

    // Continuous URL monitoring (secondary security mechanism)
    private var continuousMonitorJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "Accessibility Service Connected")
        Log.d(TAG, "═══════════════════════════════════════════")

        // Configure service
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }

        // Load model based on preference
        serviceScope.launch {
            val preferredModel = modelPreference.getSelectedModel()
            Log.i(TAG, "Loading preferred model: ${preferredModel.name}")

            isDetectionEnabled = modelPreference.isDetectionEnabled()
            Log.i(TAG, "Detection enabled: $isDetectionEnabled")

            isClassifierReady = classifierManager.switchModel(preferredModel)
            currentActiveModel = preferredModel
            Log.i(TAG, "Classifier ready: $isClassifierReady with model: ${currentActiveModel.name}")

            // Start observing preference changes
            startPreferenceObserver()
        }
    }

    /**
     * Start observing preference changes
     * Check every 2 seconds if user selected different model
     */
    private fun startPreferenceObserver() {
        preferenceCheckJob?.cancel()
        preferenceCheckJob = serviceScope.launch {
            while (isActive) {
                try {
                    val savedModel = modelPreference.getSelectedModel()
                    if (savedModel != currentActiveModel) {
                        Log.w(TAG, "Model preference changed: ${currentActiveModel.name} → ${savedModel.name}")
                        classifierManager.switchModel(savedModel)
                        currentActiveModel = savedModel
                        Log.i(TAG, "✅ Model auto-switched based on preference")
                    }

                    val detectionEnabled = modelPreference.isDetectionEnabled()
                    if (detectionEnabled != isDetectionEnabled) {
                        isDetectionEnabled = detectionEnabled
                        Log.i(TAG, if (detectionEnabled) "▶️ Detection resumed" else "⏸️ Detection paused")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking model preference", e)
                }
                delay(2000) // Check every 2 seconds
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val packageName = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(packageName)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                if (isBrowserPackage(packageName)) {
                    throttledUrlCheck()
                }
            }
        }
    }

    private fun handleWindowStateChanged(packageName: String) {
        if (isBrowserPackage(packageName)) {
            val wasInBrowser = currentBrowserPackage != null
            if (currentBrowserPackage != packageName) {
                currentBrowserPackage = packageName
                cachedUrlBarId = null
                cachedUrlBarPackage = null
                Log.d(TAG, "Switched to browser: $packageName")
            }
            checkUrlInBrowserForced()

            // Start continuous monitoring when entering browser
            if (!wasInBrowser) {
                startContinuousMonitoring()
            }
        } else if (!isSystemUiPackage(packageName)) {
            if (currentBrowserPackage != null) {
                Log.d(TAG, "Left browser, now in: $packageName")
                currentBrowserPackage = null
                lastDetectedUrl = null
                cachedUrlBarId = null
                cachedUrlBarPackage = null
                stopContinuousMonitoring()
                cancelPendingBlock()  // Cancel block if user left browser
            }
        }
    }

    /**
     * Start continuous URL monitoring while user is in browser.
     * This is a secondary security mechanism that catches URLs that bypass initial detection.
     * Runs every 1 second to check the current URL even after page loads.
     */
    private fun startContinuousMonitoring() {
        continuousMonitorJob?.cancel()

        continuousMonitorJob = serviceScope.launch {
            Log.d(TAG, "Started continuous URL monitoring")
            while (isActive && currentBrowserPackage != null) {
                delay(CONTINUOUS_MONITOR_INTERVAL_MS)

                // Double-check we're still in browser
                if (currentBrowserPackage == null) break

                // Perform URL check (forced, bypasses lastDetectedUrl optimization)
                checkUrlContinuous()
            }
            Log.d(TAG, "Stopped continuous URL monitoring")
        }
    }

    /**
     * Stop continuous URL monitoring when leaving browser.
     */
    private fun stopContinuousMonitoring() {
        continuousMonitorJob?.cancel()
        continuousMonitorJob = null
    }

    /**
     * Check URL continuously - used by continuous monitoring loop.
     */
    private fun checkUrlContinuous() {
        val url = extractUrlFromBrowser() ?: return

        // Only process if URL changed
        if (url != lastDetectedUrl) {
            lastDetectedUrl = url
            processUrl(url)
        }
    }

    private fun throttledUrlCheck() {
        val now = System.currentTimeMillis()
        if (now - lastUrlCheckTime >= URL_CHECK_THROTTLE_MS) {
            lastUrlCheckTime = now
            checkUrlInBrowser()
        }
    }

    private fun checkUrlInBrowser() {
        val url = extractUrlFromBrowser() ?: return
        if (url == lastDetectedUrl) return

        lastDetectedUrl = url
        Log.d(TAG, "URL detected: $url")
        processUrl(url)
    }

    private fun checkUrlInBrowserForced() {
        val url = extractUrlFromBrowser() ?: return
        lastDetectedUrl = url
        Log.d(TAG, "URL detected (forced): $url")
        processUrl(url)
    }

    private fun processUrl(url: String) {
        if (!isClassifierReady) {
            Log.w(TAG, "Classifier not ready")
            return
        }

        if (!isDetectionEnabled) {
            Log.d(TAG, "⏸️ Detection paused, skipping: $url")
            return
        }

        val result = classifierManager.classify(url)

        // Skip if not a valid domain (e.g., search query)
        if (result.skipped) {
            Log.d(TAG, "⏭️ Skipped (not a valid domain): $url")
            cancelPendingBlock()
            return
        }

        if (result.isAdult) {
            Log.w(TAG, "⚠️ Adult content detected: $url (score=${"%.4f".format(result.score)})")

            val record = BlockedDomainRecord(
                domain = result.domain,
                detectionTimeMs = result.inferenceTimeMs.toLong(),
                modelUsed = currentActiveModel.name,
                score = result.score,
                lexicalFeatures = result.lexicalFeatures
            )
            blocklistRepository.addBlockedDomain(record)

            scheduleBlock(url)
        } else {
            // URL is safe - cancel any pending block and show debug info overlay
            cancelPendingBlock()
            Log.d(TAG, "✅ Safe URL: ${result.domain} → model input='${result.extractedDomainName}' (score=${"%.4f".format(result.score)})")
            showDetectionInfoOverlay(
                domain = result.domain,
                extractedDomainName = result.extractedDomainName,
                score = result.score,
                isAdult = false,
                model = currentActiveModel.name,
                inferenceTimeMs = result.inferenceTimeMs,
                lexicalFeatures = result.lexicalFeatures
            )
        }
    }

    /**
     * Cancel any pending block job.
     * Called when user navigates to safe URL or deletes blocked URL.
     */
    private fun cancelPendingBlock() {
        if (pendingBlockJob?.isActive == true) {
            pendingBlockJob?.cancel()
            pendingBlockJob = null
            Log.d(TAG, "Cancelled pending block")
        }
    }

    private fun scheduleBlock(domain: String) {
        pendingBlockJob?.cancel()
        pendingBlockJob = serviceScope.launch {
            Log.d(TAG, "Scheduling block for: $domain")
            delay(BLOCK_DELAY_MS)
            executeBlock(domain)
        }
    }

    private fun executeBlock(domain: String) {
        Log.d(TAG, "Executing block for: $domain")

        // Go to home screen FIRST to close browser
        performGlobalAction(GLOBAL_ACTION_HOME)

        // Show overlay activity after small delay (same as LawanPMO)
        serviceScope.launch {
            delay(100)
            showBlockOverlay(domain)
        }
    }

    private fun showBlockOverlay(domain: String) {
        try {
            val intent = Intent(this, BlockOverlayActivity::class.java).apply {
                putExtra(BlockOverlayActivity.EXTRA_BLOCKED_DOMAIN, domain)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            Log.d(TAG, "Block overlay activity launched for: $domain")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching overlay activity", e)
        }
    }

    private fun showDetectionInfoOverlay(
        domain: String,
        extractedDomainName: String?,
        score: Float,
        isAdult: Boolean,
        model: String,
        inferenceTimeMs: Double,
        lexicalFeatures: LexicalFeatures? = null
    ) {
        try {
            val intent = Intent(this, DetectionInfoOverlayActivity::class.java).apply {
                putExtra(DetectionInfoOverlayActivity.EXTRA_DOMAIN, domain)
                putExtra(DetectionInfoOverlayActivity.EXTRA_EXTRACTED_DOMAIN_NAME, extractedDomainName)
                putExtra(DetectionInfoOverlayActivity.EXTRA_SCORE, score)
                putExtra(DetectionInfoOverlayActivity.EXTRA_IS_ADULT, isAdult)
                putExtra(DetectionInfoOverlayActivity.EXTRA_MODEL, model)
                putExtra(DetectionInfoOverlayActivity.EXTRA_INFERENCE_TIME_MS, inferenceTimeMs)
                if (lexicalFeatures != null) {
                    putExtra(DetectionInfoOverlayActivity.EXTRA_LEXICAL_FEATURES, lexicalFeatures)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            Log.d(TAG, "Detection info overlay launched: domain=$domain score=${"%.4f".format(score)} adult=$isAdult")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching detection info overlay", e)
        }
    }

    private fun extractUrlFromBrowser(): String? {
        val rootNode = rootInActiveWindow ?: return null
        // Note: recycle() is deprecated in API 34+, system handles recycling automatically
        return findUrlDirectly(rootNode)
    }

    /**
     * Find URL using known address bar view IDs.
     * This is more accurate than tree traversal as it targets the actual URL bar,
     * not autocomplete suggestions or other EditText fields.
     *
     * NOTE: Unlike findUrlInEditTexts(), this does NOT require a dot in the text.
     * The URL bar is a trusted source - if text is there, it's being typed by user.
     * This enables character-by-character detection (e.g., "king" → "kingbo" → "kingbokep").
     */
    private fun findUrlDirectly(rootNode: AccessibilityNodeInfo): String? {
        // Try cached ID first (optimization - reduces node allocations)
        if (cachedUrlBarId != null && cachedUrlBarPackage == currentBrowserPackage) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(cachedUrlBarId!!)
            if (!nodes.isNullOrEmpty()) {
                val text = nodes[0].text?.toString()
                // Accept any non-blank text that's not the placeholder
                if (!text.isNullOrBlank() && !isPlaceholderText(text)) {
                    return text
                }
            }
        }

        // Try all known address bar IDs (full package:id/view_id format)
        for (fullId in addressBarIds) {
            // Only try IDs that match current browser package
            if (!fullId.startsWith(currentBrowserPackage ?: "")) continue

            val nodes = rootNode.findAccessibilityNodeInfosByViewId(fullId)
            if (!nodes.isNullOrEmpty()) {
                val text = nodes[0].text?.toString()
                // Accept any non-blank text that's not the placeholder
                if (!text.isNullOrBlank() && !isPlaceholderText(text)) {
                    // Cache successful ID
                    cachedUrlBarId = fullId
                    cachedUrlBarPackage = currentBrowserPackage
                    Log.d(TAG, "Found URL in bar: $text")
                    return text
                }
            }
        }
        return null
    }

    /**
     * Check if text is a browser placeholder (not actual user input).
     */
    private fun isPlaceholderText(text: String): Boolean {
        val lowerText = text.lowercase()
        return lowerText.contains("search") ||
               lowerText.contains("ketik") ||
               lowerText.contains("type url") ||
               lowerText.contains("cari") ||
               lowerText.contains("telusuri") ||
               lowerText == "null"
    }

    /**
     * Fallback: search for URL in EditText nodes.
     * Only used if direct lookup fails.
     */
    private fun findUrlInEditTexts(node: AccessibilityNodeInfo?, depth: Int = 0): String? {
        if (node == null || depth > 10) return null

        if (node.className == "android.widget.EditText") {
            val text = node.text?.toString()
            if (isValidUrl(text)) {
                return text
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findUrlInEditTexts(child, depth + 1)
                if (result != null) return result
            }
        }

        return null
    }

    /**
     * Validate if text looks like a URL.
     * Filters out autocomplete suggestions and invalid text.
     */
    private fun isValidUrl(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        // Must contain dot, no spaces, and either starts with http/www or has reasonable length
        return text.contains(".") && !text.contains(" ") &&
               (text.startsWith("http") || text.contains("www") || text.length > 3)
    }

    private fun isBrowserPackage(packageName: String): Boolean {
        return packageName in browserPackages
    }

    private fun isSystemUiPackage(packageName: String): Boolean {
        return packageName in systemUiPackages
    }

    /**
     * Switch to different ML model.
     * Called from HomeScreen when user selects different model.
     * This is a suspend function - call it from a coroutine.
     */
    suspend fun switchClassificationModel(modelType: MLModelType): Boolean {
        return try {
            val success = classifierManager.switchModel(modelType)
            if (success) {
                isClassifierReady = true
                Log.i(TAG, "✅ Model switched successfully in service")
            } else {
                Log.e(TAG, "❌ Failed to switch model in service")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error switching model in service", e)
            false
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopContinuousMonitoring()
        preferenceCheckJob?.cancel()
        classifierManager.release()
        serviceScope.cancel()
    }
}
