package com.codex.whatsappguard.block

import com.codex.whatsappguard.domain.NodeQuery
import com.codex.whatsappguard.domain.OverlayTheme
import com.codex.whatsappguard.metrics
import com.codex.whatsappguard.node
import org.junit.Assert.*
import org.junit.Test

class ChatInputBlockFactoryTest {
    private val factory = ChatInputBlockFactory()
    private val theme   = OverlayTheme.fromDarkMode(false)

    @Test
    fun buildsMicrophoneBlock_whenComposerIsEmpty() {
        val query = NodeQuery(listOf(
            node(description="Back",       left=0,   top=60,   right=150,  bottom=210),
            node(description="Voice call", left=730, top=60,   right=820,  bottom=210),
            node(id="entry", text="Messaggio", left=80, top=2180, right=840, bottom=2270),
            node(id="voice_note_btn", description="Microfono", left=930, top=2180, right=1040, bottom=2290)
        ))
        val block = factory.buildMicrophoneBlock(query, metrics(), theme)
        assertNotNull(block)
        assertEquals("voice_mic", block?.key)
    }

    @Test
    fun doesNotBuildMicBlock_whenComposerHasText() {
        val query = NodeQuery(listOf(
            node(description="Back",       left=0,   top=60,   right=150, bottom=210),
            node(description="Voice call", left=730, top=60,   right=820, bottom=210),
            node(id="entry", text="ciao",  left=80,  top=2180, right=840, bottom=2270),
            node(id="voice_note_btn", description="Microfono", left=930, top=2180, right=1040, bottom=2290)
        ))
        assertNull(factory.buildMicrophoneBlock(query, metrics(), theme))
    }

    @Test
    fun doesNotBuildMicBlock_onHomeScreen() {
        val query = NodeQuery(listOf(node(id="bottom_nav_container")))
        assertNull(factory.buildMicrophoneBlock(query, metrics(), theme))
    }
}
