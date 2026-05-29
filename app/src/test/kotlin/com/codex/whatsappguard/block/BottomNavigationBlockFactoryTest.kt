package com.codex.whatsappguard.block

import com.codex.whatsappguard.domain.NodeQuery
import com.codex.whatsappguard.domain.OverlayTheme
import com.codex.whatsappguard.metrics
import com.codex.whatsappguard.node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavigationBlockFactoryTest {
    private val factory = BottomNavigationBlockFactory()
    private val theme = OverlayTheme.fromDarkMode(true)

    @Test
    fun explicitBottomNavCreatesSingleOpaqueBlock() {
        val query = NodeQuery(
            listOf(node(id = "bottom_nav_container", top = 2100, right = 1080, bottom = 2240))
        )

        val blocks = factory.build(query, metrics(navTop = 2280), theme)

        assertEquals(1, blocks.size)
        assertEquals("bottom_nav", blocks.single().key)
        assertFalse(blocks.single().color == OverlayTheme.TRANSPARENT)
    }

    @Test
    fun explicitBottomNavExtendsToAndroidNavigationBarTopWhenLabelsContinueBelowNode() {
        val query = NodeQuery(
            listOf(node(id = "bottom_nav_container", top = 2100, right = 1080, bottom = 2240))
        )

        val block = factory.build(query, metrics(navTop = 2280), theme).single()

        assertEquals(0, block.rect.left)
        assertEquals(1080, block.rect.right)
        assertEquals(2100, block.rect.top)
        assertEquals(2280, block.rect.bottom)
    }

    @Test
    fun explicitBottomNavIsClampedAboveAndroidNavigationBar() {
        val query = NodeQuery(
            listOf(node(id = "bottom_nav_container", left = 0, top = 1974, right = 1080, bottom = 2286))
        )

        val block = factory.build(query, metrics(height = 2340, navTop = 2214), theme).single()

        assertEquals(1974, block.rect.top)
        assertEquals(2214, block.rect.bottom)
    }

    @Test
    fun explicitBottomNavFarFromSystemBarKeepsItsOwnBounds() {
        val query = NodeQuery(
            listOf(node(id = "bottom_nav_container", top = 1800, right = 1080, bottom = 1900))
        )

        val block = factory.build(query, metrics(height = 2340, navTop = 2214), theme).single()

        assertEquals(1900, block.rect.bottom)
    }

    @Test
    fun legacyMetricsDoNotExtendExplicitNavIntoSystemNavigation() {
        val query = NodeQuery(
            listOf(
                node(id = "statusBarBackground", left = 0, top = 0, right = 1080, bottom = 75),
                node(id = "bottom_nav_container", left = 0, top = 1974, right = 1080, bottom = 2139)
            )
        )

        val block = factory.build(query, metrics(height = 2340, navTop = 2139), theme).single()

        assertEquals(2139, block.rect.bottom)
    }

    @Test
    fun noNavigationBarStillCoversLabelArea() {
        val query = NodeQuery(
            listOf(node(id = "bottom_nav_container", top = 2100, right = 1080, bottom = 2240))
        )

        val block = factory.build(query, metrics(navTop = null), theme).single()

        assertEquals(2400, block.rect.bottom)
    }

    @Test
    fun labelFallbackDetectsBottomNav() {
        val query = NodeQuery(
            listOf(
                node(description = "Chats", left = 0, top = 2190, right = 270, bottom = 2260),
                node(description = "Updates", left = 270, top = 2190, right = 540, bottom = 2260),
                node(description = "Calls", left = 810, top = 2190, right = 1080, bottom = 2260)
            )
        )

        assertEquals(1, factory.build(query, metrics(navTop = 2280), theme).size)
    }

    @Test
    fun returnsEmptyWhenNoMainScreenSignalExists() {
        val query = NodeQuery(listOf(node(id = "entry", text = "Messaggio", top = 2180, bottom = 2270)))

        assertTrue(factory.build(query, metrics(), theme).isEmpty())
    }
}
