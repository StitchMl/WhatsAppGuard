package com.codex.whatsappguard.block

import com.codex.whatsappguard.domain.*

/**
 * Builds overlays for chat composer controls.
 *
 * @param classifier Screen classifier.
 */
class ChatInputBlockFactory(
    private val classifier: WhatsAppScreenClassifier = WhatsAppScreenClassifier()
) {
    /** Builds a microphone overlay only while the composer is empty. */
    fun buildMicrophoneBlock(query: NodeQuery, metrics: ScreenMetrics, theme: OverlayTheme): OverlayBlock? {
        if (!classifier.isChatConversationScreen(query, metrics)) return null
        if (!classifier.isMessageEntryEmpty(query)) return null
        val node = findMicrophoneNode(query, metrics) ?: return null
        val rect = node.rect.expand(metrics.dp(4), metrics.bounds)
        return OverlayBlock("voice_mic", rect, theme.inputBackground, rect.width / 2f)
    }

    private fun findMicrophoneNode(query: NodeQuery, metrics: ScreenMetrics): NodeSnapshot? {
        query.findLargestById("voice_note_btn")
            ?.takeIf { looksLikeVoiceInput(query, it) }
            ?.let { return it }

        query.findLargestById("send_container")
            ?.takeIf { !query.hasId("send") && isRightFooterButton(it, metrics) }
            ?.let { return it }

        return query.nodes.asSequence()
            .filter { looksLikeVoiceInput(query, it) }
            .filter { it.rect.centerX > metrics.bounds.right * 0.62f }
            .filter { it.rect.width <= metrics.dp(130) && it.rect.height <= metrics.dp(130) }
            .maxByOrNull { it.rect.centerX + it.rect.centerY }
    }

    private fun looksLikeVoiceInput(query: NodeQuery, node: NodeSnapshot): Boolean =
        query.hasId(node, "voice_note_btn") || query.matchesAny(node, WhatsAppLabels.microphone)

    private fun isRightFooterButton(node: NodeSnapshot, metrics: ScreenMetrics): Boolean =
        node.rect.centerX > metrics.bounds.right * 0.62f &&
        node.rect.centerY > metrics.bounds.height * 0.70f &&
        node.rect.width  <= metrics.dp(180) &&
        node.rect.height <= metrics.dp(180)
}
