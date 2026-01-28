package com.lawanpmo.autoblocklist.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.lawanpmo.autoblocklist.data.classifier.UrlClassifier
import com.lawanpmo.autoblocklist.presentation.ui.BlockOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Accessibility Service for detecting and blocking adult URLs in browsers.
 *
 * Detection flow:
 * 1. Monitor browser URL bar for changes
 * 2. Extract domain name from URL
 * 3. Run ML classification
 * 4. Block if adult content detected (score > 0.7)
 */
class UrlBlockerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UrlBlockerService"
        private const val MODEL_PATH = "url_classifier.tflite"
        private const val URL_CHECK_THROTTLE_MS = 300L
        private const val BLOCK_DELAY_MS = 1500L
        private const val CONTINUOUS_MONITOR_INTERVAL_MS = 1000L // Check every 1 second while in browser
    }

    // Coroutine scope for async operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ML Classifier
    private lateinit var urlClassifier: UrlClassifier
    private var isClassifierReady = false

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
        Log.d(TAG, "Service connected")

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

        // Load ML model
        urlClassifier = UrlClassifier(applicationContext)
        serviceScope.launch {
            isClassifierReady = urlClassifier.loadModel(MODEL_PATH)
            Log.d(TAG, "ML Classifier ready: $isClassifierReady")
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
        processUrl(url)
    }

    private fun checkUrlInBrowserForced() {
        val url = extractUrlFromBrowser() ?: return
        lastDetectedUrl = url
        processUrl(url)
    }

    // Track last classification score for overlay
    private var lastClassificationScore: Float = 0f

    private fun processUrl(url: String) {
        if (!isClassifierReady) {
            Log.w(TAG, "Classifier not ready")
            return
        }

        val result = urlClassifier.classify(url)
        Log.d(TAG, "URL: $url -> score=${result.score}, isAdult=${result.isAdult}")

        if (result.isAdult) {
            Log.w(TAG, "Adult content detected: $url (score=${result.score})")
            lastClassificationScore = result.score
            scheduleBlock(url, result.score)
        }
    }

    private fun scheduleBlock(domain: String, score: Float) {
        pendingBlockJob?.cancel()
        pendingBlockJob = serviceScope.launch {
            Log.d(TAG, "Scheduling block for: $domain")
            delay(BLOCK_DELAY_MS)
            executeBlock(domain, score)
        }
    }

    private fun executeBlock(domain: String, score: Float) {
        Log.d(TAG, "Executing block for: $domain")

        // Go to home screen
        performGlobalAction(GLOBAL_ACTION_HOME)

        // Show overlay activity after small delay
        serviceScope.launch {
            delay(100)
            showBlockOverlay(domain, score)
        }
    }

    private fun showBlockOverlay(domain: String, score: Float) {
        try {
            val intent = Intent(this, BlockOverlayActivity::class.java).apply {
                putExtra(BlockOverlayActivity.EXTRA_BLOCKED_DOMAIN, domain)
                putExtra(BlockOverlayActivity.EXTRA_ML_SCORE, score)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            Log.d(TAG, "Block overlay activity launched for: $domain")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching overlay activity", e)
        }
    }

    private fun extractUrlFromBrowser(): String? {
        val rootNode = rootInActiveWindow ?: return null

        try {
            // Use direct lookup only - this targets specific URL bar IDs
            // Tree traversal is disabled to prevent picking up autocomplete suggestions
            return findUrlDirectly(rootNode)
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * Find URL using known address bar view IDs.
     * This is more accurate than tree traversal as it targets the actual URL bar,
     * not autocomplete suggestions or other EditText fields.
     */
    private fun findUrlDirectly(rootNode: AccessibilityNodeInfo): String? {
        // Try cached ID first (optimization - reduces node allocations)
        if (cachedUrlBarId != null && cachedUrlBarPackage == currentBrowserPackage) {
            var nodes: List<AccessibilityNodeInfo>? = null
            try {
                nodes = rootNode.findAccessibilityNodeInfosByViewId(cachedUrlBarId!!)
                if (!nodes.isNullOrEmpty()) {
                    val text = nodes[0].text?.toString()
                    Log.d(TAG, "Cached urlBarId ($cachedUrlBarId) text: $text")
                    if (isValidUrl(text)) return text
                }
            } finally {
                nodes?.forEach { it.recycle() }
            }
        }

        // Try all known address bar IDs (full package:id/view_id format)
        for (fullId in addressBarIds) {
            // Only try IDs that match current browser package
            if (!fullId.startsWith(currentBrowserPackage ?: "")) continue

            var nodes: List<AccessibilityNodeInfo>? = null
            try {
                nodes = rootNode.findAccessibilityNodeInfosByViewId(fullId)
                if (!nodes.isNullOrEmpty()) {
                    val text = nodes[0].text?.toString()
                    Log.d(TAG, "Trying urlBarId ($fullId) text: $text")
                    if (isValidUrl(text)) {
                        // Cache successful ID
                        cachedUrlBarId = fullId
                        cachedUrlBarPackage = currentBrowserPackage
                        Log.d(TAG, "Found valid URL via direct lookup: $text")
                        return text
                    }
                }
            } finally {
                nodes?.forEach { it.recycle() }
            }
        }
        Log.d(TAG, "Direct lookup failed, falling back to tree traversal")
        return null
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
            var child: AccessibilityNodeInfo? = null
            try {
                child = node.getChild(i)
                if (child != null) {
                    val result = findUrlInEditTexts(child, depth + 1)
                    if (result != null) return result
                }
            } finally {
                child?.recycle()
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

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopContinuousMonitoring()
        urlClassifier.release()
        serviceScope.cancel()
    }
}
