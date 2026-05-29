package com.codex.whatsappguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.codex.whatsappguard.domain.OverlayBuildRequest
import com.codex.whatsappguard.domain.WhatsAppPackages
import com.codex.whatsappguard.overlay.OverlayBlocker
import com.codex.whatsappguard.overlay.WhatsAppOverlayBlockBuilder
import com.codex.whatsappguard.scanner.AndroidScreenMetricsReader
import com.codex.whatsappguard.scanner.AndroidWhatsAppNodeScanner
import com.codex.whatsappguard.service.HandlerTaskScheduler
import com.codex.whatsappguard.service.OverlayRefreshScheduler

/**
 * Accessibility entry point that keeps WhatsApp overlay blocks in sync with the
 * currently visible screen.
 */
//noinspection AccessibilityPolicy
class GuardAccessibilityService : AccessibilityService() {
    private val nodeScanner = AndroidWhatsAppNodeScanner()
    private val blockBuilder = WhatsAppOverlayBlockBuilder()

    private lateinit var mainHandler: Handler
    private lateinit var displayManager: DisplayManager
    private lateinit var overlayBlocker: OverlayBlocker
    private lateinit var metricsReader: AndroidScreenMetricsReader
    private lateinit var refreshScheduler: OverlayRefreshScheduler
    private var displayListenerRegistered = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (::refreshScheduler.isInitialized) {
                refreshScheduler.scheduleSettlingRefreshes()
            }
        }
    }

    override fun onServiceConnected() {
        Log.i(TAG, "Accessibility service connected")
        serviceInfo = buildServiceInfo()
        mainHandler = Handler(Looper.getMainLooper())
        displayManager = getSystemService(DisplayManager::class.java)
        displayManager.registerDisplayListener(displayListener, mainHandler)
        displayListenerRegistered = true
        overlayBlocker = OverlayBlocker(this)
        metricsReader = AndroidScreenMetricsReader(this)
        refreshScheduler = OverlayRefreshScheduler(
            scheduler = HandlerTaskScheduler(mainHandler),
            onRefresh = ::refreshOverlay
        )
        refreshScheduler.scheduleSettlingRefreshes()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::refreshScheduler.isInitialized) return

        val packageName = event?.packageName?.toString()
        if (packageName == this.packageName) return

        val eventType = event?.eventType ?: return
        val isStructuralEvent =
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        if (isStructuralEvent) {
            refreshScheduler.scheduleSettlingRefreshes()
            return
        }

        if (packageName in WhatsAppPackages.supported) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
            ) {
                refreshScheduler.scheduleSettlingRefreshes()
                return
            }
            refreshScheduler.schedule()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::refreshScheduler.isInitialized) {
            refreshScheduler.scheduleSettlingRefreshes()
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "Accessibility service interrupted")
        shutDown()
    }

    override fun onDestroy() {
        Log.i(TAG, "Accessibility service destroyed")
        shutDown()
        super.onDestroy()
    }

    private fun refreshOverlay() {
        if (!::overlayBlocker.isInitialized || !::metricsReader.isInitialized) return

        val nodes = nodeScanner.collectNodes(this)
        if (nodes == null) {
            refreshScheduler.stopForegroundWatch()
            overlayBlocker.clear()
            return
        }

        val request = OverlayBuildRequest(nodes, metricsReader.read())
        val blocks = blockBuilder.build(request)
        overlayBlocker.show(blocks)
        refreshScheduler.startForegroundWatch()
    }

    private fun shutDown() {
        if (::refreshScheduler.isInitialized) refreshScheduler.cancel()
        if (::displayManager.isInitialized && displayListenerRegistered) {
            displayManager.unregisterDisplayListener(displayListener)
            displayListenerRegistered = false
        }
        if (::overlayBlocker.isInitialized) overlayBlocker.clear()
    }

    private fun buildServiceInfo(): AccessibilityServiceInfo {
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
        info.flags =
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 0L
        return info
    }

    private companion object {
        const val TAG = "WhatsAppGuard"
    }
}
