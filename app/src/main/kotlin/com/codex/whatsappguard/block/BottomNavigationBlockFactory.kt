package com.codex.whatsappguard.block

import com.codex.whatsappguard.domain.IntRect
import com.codex.whatsappguard.domain.NodeQuery
import com.codex.whatsappguard.domain.NodeSnapshot
import com.codex.whatsappguard.domain.OverlayBlock
import com.codex.whatsappguard.domain.OverlayTheme
import com.codex.whatsappguard.domain.ScreenMetrics
import com.codex.whatsappguard.domain.WhatsAppGeometry
import com.codex.whatsappguard.domain.WhatsAppLabels
import com.codex.whatsappguard.domain.WhatsAppScreenClassifier
import kotlin.math.max
import kotlin.math.min

/**
 * Builds the opaque block that hides and disables the WhatsApp bottom navigation bar.
 *
 * @param classifier Screen classifier used by the home-screen fallback.
 * @param geometry Shared geometry helper.
 */
class BottomNavigationBlockFactory(
    private val classifier: WhatsAppScreenClassifier = WhatsAppScreenClassifier(),
    private val geometry: WhatsAppGeometry = WhatsAppGeometry()
) {
    /**
     * Returns the bottom navigation overlay for WhatsApp home screens.
     *
     * @param query Current WhatsApp accessibility nodes.
     * @param metrics Current screen metrics.
     * @param theme Current overlay palette.
     */
    fun build(query: NodeQuery, metrics: ScreenMetrics, theme: OverlayTheme): List<OverlayBlock> {
        val rect = bottomNavRect(query, metrics) ?: return emptyList()
        return listOf(OverlayBlock("bottom_nav", rect.clamp(metrics.bounds), theme.pageBackground))
    }

    private fun bottomNavRect(query: NodeQuery, metrics: ScreenMetrics): IntRect? {
        val base = explicitBottomNav(query)
            ?: labelBasedBottomNav(query, metrics)
            ?: homeShellFallback(query, metrics)
            ?: return null

        val bottomLimit = geometry.overlayBottomLimit(query, metrics)
        val top = max(metrics.bounds.top, base.top)
        val bottom = navigationAwareBottom(base.bottom, bottomLimit, metrics)
        if (bottom <= top) return null

        return IntRect(metrics.bounds.left, top, metrics.bounds.right, bottom)
    }

    private fun navigationAwareBottom(baseBottom: Int, bottomLimit: Int, metrics: ScreenMetrics): Int {
        val distanceFromSystemBar = bottomLimit - baseBottom
        if (distanceFromSystemBar in 0..metrics.dp(BOTTOM_EXTENSION_THRESHOLD_DP)) {
            return bottomLimit
        }
        return min(baseBottom, bottomLimit)
    }

    private fun explicitBottomNav(query: NodeQuery): IntRect? {
        return query.findLargestById("bottom_nav_container")?.rect
            ?: query.findLargestById("bottom_nav")?.rect
    }

    private fun labelBasedBottomNav(query: NodeQuery, metrics: ScreenMetrics): IntRect? {
        val candidates = bottomNavLabelCandidates(query, metrics)
        if (candidates.size < 2) return null

        val top = candidates.minOf { candidate ->
            if (candidate.rect.height > metrics.dp(44)) candidate.rect.top
            else candidate.rect.top - metrics.dp(64)
        }
        val appBottom = geometry.appBottom(query, metrics)
        val bottom = min(candidates.maxOf { it.rect.bottom } + metrics.dp(24), appBottom)
        return IntRect(metrics.bounds.left, top, metrics.bounds.right, bottom)
    }

    private fun bottomNavLabelCandidates(query: NodeQuery, metrics: ScreenMetrics): List<NodeSnapshot> =
        query.nodes.asSequence()
            .filter { query.matchesExactOrPrefixed(it, WhatsAppLabels.bottomNavigation) }
            .filter { it.rect.centerY > metrics.bounds.height * 0.72f }
            .filter { it.rect.width <= metrics.bounds.width && it.rect.height <= metrics.dp(220) }
            .toList()

    private fun homeShellFallback(query: NodeQuery, metrics: ScreenMetrics): IntRect? {
        if (!classifier.isMainScreen(query, metrics)) return null

        val visibleBottom = geometry.visibleAppBottom(query, metrics) ?: return null
        val bottom = min(visibleBottom + metrics.dp(72), geometry.appBottom(query, metrics))
        return IntRect(metrics.bounds.left, visibleBottom, metrics.bounds.right, bottom)
    }

    private companion object {
        const val BOTTOM_EXTENSION_THRESHOLD_DP = 96
    }
}
