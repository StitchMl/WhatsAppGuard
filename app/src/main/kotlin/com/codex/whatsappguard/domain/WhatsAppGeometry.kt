package com.codex.whatsappguard.domain

import kotlin.math.max

/**
 * Geometry calculations shared by overlay factories.
 */
class WhatsAppGeometry {
    /**
     * Returns the lowest coordinate where WhatsApp overlays may intercept touches.
     *
     * Android exposes full-screen coordinates to accessibility overlays, but on
     * three-button navigation devices the area below [ScreenMetrics.navigationBarTop]
     * belongs to the system navigation bar. Blocking views must never extend there.
     *
     * @param metrics Current screen metrics.
     */
    fun touchSafeBottom(metrics: ScreenMetrics): Int {
        val screen = metrics.bounds
        return metrics.navigationBarTop
            ?.takeIf { it in (screen.height / 2)..screen.bottom }
            ?: metrics.usableScreenBottom
                ?.takeIf { it in (screen.height / 2)..screen.bottom }
            ?: screen.bottom
    }

    /**
     * Returns the lower edge of the selected WhatsApp window.
     *
     * @param query Current node query.
     * @param metrics Current screen metrics.
     */
    fun appBottom(query: NodeQuery, metrics: ScreenMetrics): Int {
        return (rawAppBottom(query, metrics) ?: overlayBottomLimit(query, metrics))
            .coerceAtMost(overlayBottomLimit(query, metrics))
    }

    /**
     * Returns the lower edge available for overlay blocks on the current WhatsApp screen.
     *
     * The system navigation inset is preferred while it is active. If WhatsApp grows
     * beyond that value after an OEM hides the navigation bar, the visible WhatsApp
     * root becomes the new limit instead of leaving stale blocks behind.
     *
     * @param query Current node query.
     * @param metrics Current screen metrics.
     */
    fun overlayBottomLimit(query: NodeQuery, metrics: ScreenMetrics): Int {
        val screen = metrics.bounds
        val safeBottom = touchSafeBottom(metrics)
        if (metrics.navigationBarTop != null) return safeBottom

        return max(safeBottom, rawAppBottom(query, metrics) ?: safeBottom)
            .coerceAtMost(screen.bottom)
    }

    private fun rawAppBottom(query: NodeQuery, metrics: ScreenMetrics): Int? {
        val screen = metrics.bounds
        return query.nodes.asSequence()
            .filter { it.rect.left <= screen.left && it.rect.right >= screen.right }
            .map { it.rect.bottom }
            .filter { it in (screen.height / 2)..screen.bottom }
            .maxOrNull()
            ?: query.nodes.maxOfOrNull { it.rect.bottom }?.coerceAtMost(screen.bottom)
    }

    /**
     * Returns the best visible lower edge for home-screen fallback overlays.
     *
     * @param query Current node query.
     * @param metrics Current screen metrics.
     */
    fun visibleAppBottom(query: NodeQuery, metrics: ScreenMetrics): Int? {
        val screen = metrics.bounds
        val safeBottom = touchSafeBottom(metrics)
        return query.nodes.asSequence()
            .map { it.rect.bottom }
            .filter { it in (screen.height / 2)..(safeBottom - metrics.dp(24)) }
            .maxOrNull()
            ?: appBottom(query, metrics).takeIf { it < safeBottom - metrics.dp(24) }
    }

    /**
     * Reads the top system inset from the WhatsApp tree when Android exposes it.
     *
     * On some Android 10 builds, DisplayMetrics reports app height without the top
     * status inset while WhatsApp draws the bottom tabs in full-screen coordinates.
     *
     * @param query Current node query.
     * @param metrics Current screen metrics.
     */
    fun topSystemInset(query: NodeQuery, metrics: ScreenMetrics): Int {
        val statusBar = query.findLargestById("statusBarBackground") ?: return 0
        if (statusBar.rect.top > metrics.bounds.top) return 0

        return statusBar.rect.height
            .takeIf { it in 1..metrics.dp(48) }
            ?: 0
    }
}
