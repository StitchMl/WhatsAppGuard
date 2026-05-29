package com.codex.whatsappguard.block

import com.codex.whatsappguard.domain.*

/** Builds overlay blocks for top bar actions (home screen only). */
class TopActionBlockFactory {
    /** Builds an opaque block for the three-dot overflow menu. */
    fun buildMoreBlock(query: NodeQuery, metrics: ScreenMetrics, theme: OverlayTheme): OverlayBlock? {
        query.findLargestById("menuitem_overflow")?.let {
            return OverlayBlock("top_more", it.rect.clamp(metrics.bounds), theme.pageBackground)
        }
        val node = query.findTopRightNode(WhatsAppLabels.more, metrics) ?: return null
        return OverlayBlock("top_more", node.rect.expand(metrics.dp(6), metrics.bounds), theme.pageBackground)
    }

    /** Builds an opaque block for the home camera action. */
    fun buildCameraBlock(query: NodeQuery, metrics: ScreenMetrics, theme: OverlayTheme): OverlayBlock? {
        query.findLargestById("menuitem_camera")?.let {
            return OverlayBlock("top_camera", it.rect.clamp(metrics.bounds), theme.pageBackground)
        }
        val node = query.findTopRightNode(WhatsAppLabels.camera, metrics) ?: return null
        return OverlayBlock("top_camera", node.rect.expand(metrics.dp(6), metrics.bounds), theme.pageBackground)
    }
}
