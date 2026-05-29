package com.codex.whatsappguard.overlay

import com.codex.whatsappguard.block.*
import com.codex.whatsappguard.domain.*

/**
 * Assembles the complete set of overlay blocks required for a given WhatsApp screen.
 *
 * Each factory collaborator owns a single UI region. This class is the sole coordinator
 * that decides which factories to invoke per screen branch — geometry logic stays in factories.
 */
class WhatsAppOverlayBlockBuilder(
    private val classifier:        WhatsAppScreenClassifier    = WhatsAppScreenClassifier(),
    private val topActions:        TopActionBlockFactory       = TopActionBlockFactory(),
    private val bottomNavigation:  BottomNavigationBlockFactory = BottomNavigationBlockFactory(classifier),
    private val floatingActions:   FloatingActionBlockFactory  = FloatingActionBlockFactory(),
    private val chatHeader:        ChatHeaderBlockFactory      = ChatHeaderBlockFactory(classifier),
    private val chatInput:         ChatInputBlockFactory       = ChatInputBlockFactory(classifier)
) {
    fun build(request: OverlayBuildRequest): List<OverlayBlock> {
        if (request.nodes.isEmpty()) return emptyList()

        val query = NodeQuery(request.nodes)
        if (classifier.isChatInfoScreen(query)) return emptyList()

        val theme  = OverlayTheme.fromDarkMode(request.metrics.isDarkMode)
        val blocks = linkedMapOf<String, OverlayBlock>()

        if (classifier.isMainScreen(query, request.metrics)) {
            addHomeScreenBlocks(blocks, query, request.metrics, theme)
        } else {
            addChatScreenBlocks(blocks, query, request.metrics, theme)
        }
        return blocks.values.toList()
    }

    /**
     * Home-shell blocks: opaque more-options + camera (hidden visually), bottom nav, Meta AI FAB.
     */
    private fun addHomeScreenBlocks(
        blocks: MutableMap<String, OverlayBlock>,
        query: NodeQuery, metrics: ScreenMetrics, theme: OverlayTheme
    ) {
        blocks.putIfNotNull(topActions.buildMoreBlock(query, metrics, theme))
        blocks.putIfNotNull(topActions.buildCameraBlock(query, metrics, theme))
        bottomNavigation.build(query, metrics, theme).forEach { blocks[it.key] = it }
        blocks.putIfNotNull(floatingActions.buildMetaAiBlock(query, metrics, theme))
    }

    /**
     * In-chat blocks: two invisible header blockers (identity + overflow) and
     * optionally the microphone block when the composer is empty.
     */
    private fun addChatScreenBlocks(
        blocks: MutableMap<String, OverlayBlock>,
        query: NodeQuery, metrics: ScreenMetrics, theme: OverlayTheme
    ) {
        blocks.putIfNotNull(chatHeader.buildIdentityBlock(query, metrics, theme))
        blocks.putIfNotNull(chatHeader.buildMoreOptionsBlock(query, metrics, theme))
        blocks.putIfNotNull(chatInput.buildMicrophoneBlock(query, metrics, theme))
    }

    private fun MutableMap<String, OverlayBlock>.putIfNotNull(block: OverlayBlock?) {
        if (block != null) this[block.key] = block
    }
}
