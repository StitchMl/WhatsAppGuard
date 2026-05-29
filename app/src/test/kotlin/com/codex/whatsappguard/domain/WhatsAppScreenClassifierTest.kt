package com.codex.whatsappguard.domain

import com.codex.whatsappguard.metrics
import com.codex.whatsappguard.node
import org.junit.Assert.*
import org.junit.Test

class WhatsAppScreenClassifierTest {
    private val classifier = WhatsAppScreenClassifier()

    @Test fun mainScreen_fromBottomNavId() {
        val q = NodeQuery(listOf(node(id="bottom_nav_container")))
        assertTrue(classifier.isMainScreen(q, metrics()))
        assertFalse(classifier.isChatConversationScreen(q, metrics()))
    }

    @Test fun chatInfo_excludesChatConversation() {
        val q = NodeQuery(listOf(node(id="contact_details_card"), node(id="entry")))
        assertTrue(classifier.isChatInfoScreen(q))
        assertFalse(classifier.isChatConversationScreen(q, metrics()))
    }

    @Test fun messageEntry_emptyVsNonEmpty() {
        val empty    = NodeQuery(listOf(node(id="entry", text="Messaggio")))
        val nonEmpty = NodeQuery(listOf(node(id="entry", text="ciao")))
        assertTrue(classifier.isMessageEntryEmpty(empty))
        assertFalse(classifier.isMessageEntryEmpty(nonEmpty))
    }
}
