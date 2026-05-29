package com.codex.whatsappguard.domain

/** Classifies WhatsApp screens from accessibility snapshots. */
class WhatsAppScreenClassifier {
    fun isChatInfoScreen(query: NodeQuery): Boolean =
        query.hasId("contact_details_card") ||
        query.hasId("top_info_card") ||
        query.hasId("chat_info_media_card_view") ||
        query.hasId("contact_info_security_card") ||
        query.hasId("block_and_report_contact_card")

    fun isMainScreen(query: NodeQuery, metrics: ScreenMetrics): Boolean =
        query.hasId("bottom_nav_container") ||
        query.hasId("bottom_nav") ||
        looksLikeHomeShell(query, metrics)

    fun isChatConversationScreen(query: NodeQuery, metrics: ScreenMetrics): Boolean {
        if (isChatInfoScreen(query) || isMainScreen(query, metrics)) return false
        val hasInput    = query.hasId("entry") || query.hasId("voice_note_btn") || query.hasId("footer")
        val hasTopAction = query.findTopRightNode(WhatsAppLabels.call + WhatsAppLabels.videoCall, metrics) != null
        return hasInput || hasTopAction
    }

    fun isMessageEntryEmpty(query: NodeQuery): Boolean {
        val entry = query.findLargestById("entry") ?: return true
        val value = query.normalizedText(entry)
        return value.isBlank() || WhatsAppLabels.messagePlaceholders.any {
            value == TextNormalizer().normalize(it)
        }
    }

    private fun looksLikeHomeShell(query: NodeQuery, metrics: ScreenMetrics): Boolean =
        hasMainSearch(query, metrics) || hasHomeContainer(query) ||
        hasHomeTitle(query, metrics) || hasLockedChats(query)

    private fun hasMainSearch(query: NodeQuery, metrics: ScreenMetrics): Boolean =
        query.nodes.any {
            query.textOrDescriptionMatchesAny(it, WhatsAppLabels.search) &&
            it.rect.centerY < metrics.bounds.height * 0.3f
        }

    private fun hasHomeContainer(query: NodeQuery): Boolean =
        query.hasId("conversation_list_view_host") || query.hasId("pager") ||
        query.hasId("updates_list") || query.hasId("conversations_coordinator_layout")

    private fun hasHomeTitle(query: NodeQuery, metrics: ScreenMetrics): Boolean =
        query.nodes.any {
            query.textOrDescriptionMatchesAny(it, listOf("whatsapp", "aggiornamenti", "updates")) &&
            it.rect.centerY < metrics.bounds.height * 0.18f
        }

    private fun hasLockedChats(query: NodeQuery): Boolean =
        query.nodes.any {
            query.textOrDescriptionMatchesAny(it, listOf("locked chats", "chat bloccate", "archiviate"))
        }
}
