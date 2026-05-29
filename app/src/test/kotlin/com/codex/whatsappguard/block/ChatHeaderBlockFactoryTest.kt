package com.codex.whatsappguard.block

import com.codex.whatsappguard.domain.OverlayTheme
import com.codex.whatsappguard.domain.NodeQuery
import com.codex.whatsappguard.node
import com.codex.whatsappguard.metrics
import org.junit.Assert.*
import org.junit.Test

class ChatHeaderBlockFactoryTest {
    private val factory = ChatHeaderBlockFactory()
    private val theme   = OverlayTheme.fromDarkMode(true)

    // ── buildIdentityBlock ────────────────────────────────────────────────

    @Test
    fun identityBlock_transparent_betweenBackAndCallButtons() {
        val query = NodeQuery(chatNodes())
        val block = factory.buildIdentityBlock(query, metrics(), theme)
        assertNotNull(block)
        assertEquals("chat_header_identity_touch", block?.key)
        assertEquals(OverlayTheme.TRANSPARENT, block?.color)
        assertTrue(block!!.rect.left > 150)   // after back button
        assertTrue(block.rect.right  < 720)   // before video-call
    }

    @Test fun identityBlock_nullOnHomeScreen() {
        assertNull(factory.buildIdentityBlock(NodeQuery(listOf(node(id = "bottom_nav_container"))), metrics(), theme))
    }

    @Test fun identityBlock_nullWhenNoRoom() {
        val q = NodeQuery(listOf(node(id = "entry", text = "Messaggio", top = 760, bottom = 830)))
        assertNull(factory.buildIdentityBlock(q, metrics(width = 220, height = 900, navTop = 840, density = 1f), theme))
    }

    // ── buildMoreOptionsBlock ─────────────────────────────────────────────

    @Test
    fun moreOptionsBlock_transparent_coversOverflow() {
        val block = factory.buildMoreOptionsBlock(NodeQuery(chatNodes()), metrics(), theme)
        assertNotNull(block)
        assertEquals("chat_more_options_touch", block?.key)
        assertEquals(OverlayTheme.TRANSPARENT, block?.color)
        assertTrue(block!!.rect.left  <= 940)
        assertTrue(block.rect.right   >= 1040)
    }

    @Test fun moreOptionsBlock_fallbackToLabel() {
        val nodes = listOf(
            node(description = "Back",         left=0,   top=60, right=150,  bottom=210),
            node(description = "Voice call",   left=730, top=60, right=820,  bottom=210),
            node(description = "More options", left=840, top=60, right=940,  bottom=210),
            node(id = "entry", text = "Messaggio", left=80, top=2180, right=850, bottom=2270)
        )
        val block = factory.buildMoreOptionsBlock(NodeQuery(nodes), metrics(), theme)
        assertNotNull(block)
        assertEquals(OverlayTheme.TRANSPARENT, block?.color)
    }

    @Test fun moreOptionsBlock_nullOnHomeScreen() {
        assertNull(factory.buildMoreOptionsBlock(NodeQuery(listOf(node(id="bottom_nav_container"))), metrics(), theme))
    }

    @Test fun moreOptionsBlock_nullWhenNoOverflow() {
        val nodes = listOf(
            node(description="Back",       left=0,   top=60, right=150, bottom=210),
            node(description="Voice call", left=730, top=60, right=820, bottom=210),
            node(id="entry", text="Messaggio", left=80, top=2180, right=850, bottom=2270)
        )
        assertNull(factory.buildMoreOptionsBlock(NodeQuery(nodes), metrics(), theme))
    }

    // ── Combination: blocks must not overlap call buttons ─────────────────

    @Test
    fun identityAndMoreOptions_doNotOverlapCallButtons() {
        val query    = NodeQuery(chatNodes())
        val identity = factory.buildIdentityBlock(query, metrics(), theme)
        val more     = factory.buildMoreOptionsBlock(query, metrics(), theme)
        assertNotNull(identity); assertNotNull(more)
        assertTrue(identity!!.rect.right  <= 720)   // stops before video-call (left=720)
        assertTrue(more!!.rect.left       >= 720)   // starts after video-call
    }

    private fun chatNodes() = listOf(
        node(description="Back",              left=0,   top=60,   right=150,  bottom=210),
        node(text="Mario",                    left=180, top=72,   right=520,  bottom=140),
        node(description="Video call",        left=720, top=60,   right=810,  bottom=210),
        node(description="Voice call",        left=830, top=60,   right=920,  bottom=210),
        node(id="menuitem_overflow",          left=940, top=60,   right=1040, bottom=210),
        node(id="entry", text="Messaggio",    left=80,  top=2180, right=850,  bottom=2270)
    )
}
