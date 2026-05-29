package com.codex.whatsappguard.block

import com.codex.whatsappguard.domain.*

/**
 * Builds transparent touch blockers for the WhatsApp chat header toolbar.
 *
 * Responsible exclusively for the in-chat top bar: the identity area (name/avatar) and the
 * overflow menu. Back, voice-call and video-call buttons are intentionally left uncovered so
 * the user can still navigate and communicate.
 *
 * @param classifier Screen classifier used to guard against wrong-screen builds.
 */
class ChatHeaderBlockFactory(
    private val classifier: WhatsAppScreenClassifier = WhatsAppScreenClassifier()
) {
    /**
     * Invisible blocker over the chat name/avatar area (prevents opening chat info screen).
     * Spans from the right edge of Back to the left edge of the first call action.
     */
    fun buildIdentityBlock(query: NodeQuery, metrics: ScreenMetrics, theme: OverlayTheme): OverlayBlock? {
        if (!classifier.isChatConversationScreen(query, metrics)) return null

        val top    = headerTop(query, metrics)
        val left   = identityLeft(query, metrics)
        val right  = identityRight(query, metrics)
        val bottom = headerBottom(query, metrics, top)

        if (right <= left || bottom <= top) return null
        return OverlayBlock(
            key   = "chat_header_identity_touch",
            rect  = IntRect(left, top, right, bottom).clamp(metrics.bounds),
            color = theme.transparent
        )
    }

    /**
     * Invisible blocker over the overflow ("⋮") button.
     * Voice-call and video-call buttons are intentionally left outside this block.
     */
    fun buildMoreOptionsBlock(query: NodeQuery, metrics: ScreenMetrics, theme: OverlayTheme): OverlayBlock? {
        if (!classifier.isChatConversationScreen(query, metrics)) return null
        val node = query.findLargestById("menuitem_overflow")
            ?: query.findTopRightNode(WhatsAppLabels.more, metrics)
            ?: return null
        return OverlayBlock(
            key   = "chat_more_options_touch",
            rect  = node.rect.expand(metrics.dp(6), metrics.bounds),
            color = theme.transparent
        )
    }

    private fun headerTop(query: NodeQuery, metrics: ScreenMetrics): Int {
        val top = allHeaderInteractiveNodes(query, metrics).minOfOrNull { it.rect.top }
        return ((top ?: (metrics.bounds.top + metrics.dp(24))))
            .coerceIn(metrics.bounds.top, metrics.bounds.top + metrics.dp(96))
    }

    private fun identityLeft(query: NodeQuery, metrics: ScreenMetrics): Int {
        val backNode = query.findTopLeftNode(WhatsAppLabels.back, metrics)
        return backNode?.rect?.right?.plus(metrics.dp(2)) ?: metrics.dp(72)
    }

    /**
     * Right boundary = left edge of the first CALL button (voice or video).
     * The overflow button is handled separately by [buildMoreOptionsBlock].
     */
    private fun identityRight(query: NodeQuery, metrics: ScreenMetrics): Int {
        val callLeft = callActionNodes(query, metrics)
            .map { it.rect.left }
            .filter { it > metrics.bounds.right * 0.45f }
            .minOrNull()
        return (callLeft ?: (metrics.bounds.right - metrics.dp(168))) - metrics.dp(2)
    }

    private fun headerBottom(query: NodeQuery, metrics: ScreenMetrics, top: Int): Int {
        val bottom = allHeaderInteractiveNodes(query, metrics)
            .maxOfOrNull { it.rect.bottom + metrics.dp(2) }
        return maxOf(bottom ?: 0, top + metrics.dp(48)).coerceAtMost(top + metrics.dp(64))
    }

    private fun allHeaderInteractiveNodes(query: NodeQuery, metrics: ScreenMetrics): List<NodeSnapshot> =
        query.topNodesMatching(WhatsAppLabels.back + WhatsAppLabels.videoCall + WhatsAppLabels.call, metrics)

    private fun callActionNodes(query: NodeQuery, metrics: ScreenMetrics): List<NodeSnapshot> =
        query.topNodesMatching(WhatsAppLabels.videoCall + WhatsAppLabels.call, metrics)
}
