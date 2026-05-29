package com.codex.whatsappguard.scanner

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import com.codex.whatsappguard.domain.IntRect
import com.codex.whatsappguard.domain.ScreenMetrics

/**
 * Reads device metrics needed by pure overlay calculations.
 *
 * @param service Accessibility service used to access window and resource data.
 */
class AndroidScreenMetricsReader(private val service: AccessibilityService) {
    fun read(): ScreenMetrics {
        val screen = screenBounds()
        val usableBottom = appUsableScreenBottom(screen)
        return ScreenMetrics(
            bounds           = screen,
            navigationBarTop = visibleNavigationBarTop(screen, usableBottom),
            density          = service.resources.displayMetrics.density,
            isDarkMode       = isDarkMode(),
            usableScreenBottom = usableBottom
        )
    }

    private fun screenBounds(): IntRect {
        val wm = service.getSystemService(WindowManager::class.java)
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
        return IntRect(0, 0, dm.widthPixels, dm.heightPixels)
    }

    private fun visibleNavigationBarTop(screen: IntRect, usableBottom: Int?): Int? {
        val systemWindowTop = navigationBarTopFromSystemWindows(screen)

        if (usableBottom != null && systemWindowTop != null) {
            if (usableBottom > systemWindowTop + EDGE_TOLERANCE_PX) return null
            return systemWindowTop
        }

        systemWindowTop?.let { return it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = windowInsets()
            if (insets.isVisible(WindowInsets.Type.navigationBars())) {
                val bottomInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                if (bottomInset > 0) return screen.bottom - bottomInset
            }
        }
        return usableBottom?.takeIf { it < screen.bottom - EDGE_TOLERANCE_PX }
    }

    private fun navigationBarTopFromSystemWindows(screen: IntRect): Int? =
        service.windows.asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_SYSTEM }
            .map {
                val rect = android.graphics.Rect()
                it.getBoundsInScreen(rect)
                IntRect(rect.left, rect.top, rect.right, rect.bottom)
            }
            .filter { it.bottom >= screen.bottom - EDGE_TOLERANCE_PX }
            .filter { it.top in (screen.height / 2)..screen.bottom }
            .filter { it.width >= screen.width / 2 }
            .filter { it.height in 1..(screen.height / 4) }
            .minOfOrNull { it.top }

    private fun appUsableScreenBottom(screen: IntRect): Int? {
        val wm = service.getSystemService(WindowManager::class.java)
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getMetrics(dm)
        return dm.heightPixels
            .coerceAtMost(screen.bottom)
            .takeIf { it in (screen.height / 2)..screen.bottom }
    }

    private fun isDarkMode(): Boolean {
        val mode = service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    @SuppressLint("NewApi")
    private fun windowInsets(): WindowInsets =
        service.getSystemService(WindowManager::class.java).currentWindowMetrics.windowInsets

    private companion object {
        const val EDGE_TOLERANCE_PX = 2
    }
}
