package com.codex.whatsappguard.overlay

import com.codex.whatsappguard.domain.OverlayBlock
import com.codex.whatsappguard.domain.OverlayBuildRequest
import com.codex.whatsappguard.domain.OverlayTheme
import com.codex.whatsappguard.metrics
import com.codex.whatsappguard.node
import org.junit.Assert.*
import org.junit.Test

class WhatsAppOverlayBlockBuilderTest {
    private val builder = WhatsAppOverlayBlockBuilder()

    @Test
    fun homeScreen_opaqueMoreAndCamera_present() {
        val blocks = builder.build(homeRequest())
        assertTrue(blocks.any { it.key == "bottom_nav" })
        assertTrue(blocks.any { it.key == "top_camera" })
        val more = blocks.firstOrNull { it.key == "top_more" }
        assertFalse("home overflow must be opaque", more?.color == OverlayTheme.TRANSPARENT)
    }

    @Test
    fun homeScreen_noChatBlocks() {
        val blocks = builder.build(homeRequest())
        assertFalse(blocks.any { it.key == "chat_header_identity_touch" })
        assertFalse(blocks.any { it.key == "chat_more_options_touch" })
    }

    @Test
    fun chatScreen_bothHeaderBlocksAreTransparent() {
        val blocks   = builder.build(chatRequest())
        val identity = blocks.firstOrNull { it.key == "chat_header_identity_touch" }
        val more     = blocks.firstOrNull { it.key == "chat_more_options_touch" }
        assertNotNull(identity); assertNotNull(more)
        assertEquals(OverlayTheme.TRANSPARENT, identity?.color)
        assertEquals(OverlayTheme.TRANSPARENT, more?.color)
    }

    @Test
    fun chatScreen_noOpaqueHomeBlocks() {
        val blocks = builder.build(chatRequest())
        assertFalse(blocks.any { it.key == "bottom_nav" })
        assertFalse(blocks.any { it.key == "top_more" })   // opaque home version absent
        assertFalse(blocks.any { it.key == "top_camera" })
    }

    @Test
    fun chatInfoScreen_returnsEmpty() {
        val req = OverlayBuildRequest(
            nodes   = listOf(node(id="contact_details_card"), node(id="entry")),
            metrics = metrics()
        )
        assertEquals(emptyList<OverlayBlock>(), builder.build(req))
    }

    @Test fun emptyNodes_returnsEmpty() {
        assertEquals(emptyList<OverlayBlock>(), builder.build(OverlayBuildRequest(emptyList(), metrics())))
    }

    private fun homeRequest() = OverlayBuildRequest(
        nodes = listOf(
            node(id="bottom_nav_container", top=2100, right=1080, bottom=2260),
            node(id="menuitem_camera",      left=810, top=70, right=900,  bottom=160),
            node(id="menuitem_overflow",    left=940, top=70, right=1030, bottom=160),
            node(id="extended_mini_fab",    left=910, top=1850, right=1030, bottom=1970)
        ),
        metrics = metrics()
    )

    private fun chatRequest() = OverlayBuildRequest(
        nodes = listOf(
            node(description="Back",       left=0,   top=60,   right=150,  bottom=210),
            node(description="Voice call", left=830, top=60,   right=920,  bottom=210),
            node(id="menuitem_overflow",   left=940, top=60,   right=1040, bottom=210),
            node(id="entry", text="Messaggio",         left=100, top=2180, right=840,  bottom=2280),
            node(id="voice_note_btn", description="Microfono", left=930, top=2180, right=1040, bottom=2290)
        ),
        metrics = metrics()
    )
}
