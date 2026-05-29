package com.codex.whatsappguard.block

import com.codex.whatsappguard.domain.*

/**
 * Builds overlays for floating WhatsApp actions such as Meta AI.
 *
 * @param geometry Geometry helper.
 */
class FloatingActionBlockFactory(
    private val geometry: WhatsAppGeometry = WhatsAppGeometry()
) {
    fun buildMetaAiBlock(query: NodeQuery, metrics: ScreenMetrics, theme: OverlayTheme): OverlayBlock {
        val node = query.findLargestById("extended_mini_fab") ?: findMetaAiNode(query, metrics)
        if (node != null) {
            return OverlayBlock(
                key           = "meta_ai",
                rect          = node.rect.expand(metrics.dp(6), metrics.bounds),
                color         = theme.pageBackground,
                cornerRadiusPx = metrics.dp(22).toFloat()
            )
        }
        return fallbackMetaAiBlock(query, metrics, theme)
    }

    private fun findMetaAiNode(query: NodeQuery, metrics: ScreenMetrics): NodeSnapshot? =
        query.nodes.asSequence()
            .filter { query.matchesAny(it, WhatsAppLabels.metaAi) }
            .filter { it.rect.centerX > metrics.bounds.right * 0.55f }
            .filter { it.rect.centerY > geometry.appBottom(query, metrics) * 0.35f }
            .filter { it.rect.width <= metrics.dp(140) && it.rect.height <= metrics.dp(140) }
            .maxByOrNull { it.rect.centerY }

    private fun fallbackMetaAiBlock(query: NodeQuery, metrics: ScreenMetrics, theme: OverlayTheme): OverlayBlock {
        val size   = metrics.dp(52)
        val right  = metrics.bounds.right - metrics.dp(24)
        val bottom = geometry.appBottom(query, metrics) - metrics.dp(148)
        return OverlayBlock(
            key           = "meta_ai_fallback",
            rect          = IntRect(right - size, bottom - size, right, bottom),
            color         = theme.pageBackground,
            cornerRadiusPx = metrics.dp(18).toFloat()
        )
    }
}
