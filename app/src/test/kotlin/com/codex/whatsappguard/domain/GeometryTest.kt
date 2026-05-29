package com.codex.whatsappguard.domain

import com.codex.whatsappguard.metrics
import com.codex.whatsappguard.node
import org.junit.Assert.*
import org.junit.Test

class GeometryTest {
    @Test fun width_and_height() {
        val r = IntRect(10, 20, 110, 70)
        assertEquals(100, r.width); assertEquals(50, r.height)
    }

    @Test fun clamp_restrictsInsideBounds() {
        val bounds = IntRect(0, 0, 100, 200)
        val clamped = IntRect(-10, -10, 150, 250).clamp(bounds)
        assertEquals(IntRect(0, 0, 100, 200), clamped)
    }

    @Test fun expand_addsMarginUpToScreenEdge() {
        val bounds  = IntRect(0, 0, 1080, 2400)
        val base    = IntRect(10, 10, 50, 50)
        val expanded = base.expand(5, bounds)
        assertEquals(5, expanded.left); assertEquals(5, expanded.top)
        assertEquals(55, expanded.right); assertEquals(55, expanded.bottom)
    }

    @Test fun area_isZeroForEmpty() {
        assertEquals(0, IntRect(5, 5, 5, 5).area)
        assertEquals(0, IntRect(5, 5, 3, 3).area)
    }

    @Test fun touchSafeBottom_usesNavigationBarTopWhenPresent() {
        val geometry = WhatsAppGeometry()

        assertEquals(2214, geometry.touchSafeBottom(metrics(height = 2340, navTop = 2214, usableBottom = 2139)))
    }

    @Test fun touchSafeBottom_usesUsableBottomWhenNavigationBarIsHidden() {
        val geometry = WhatsAppGeometry()

        assertEquals(2340, geometry.touchSafeBottom(metrics(height = 2340, navTop = null, usableBottom = 2340)))
    }

    @Test fun appBottom_neverExtendsIntoAndroidNavigationBar() {
        val geometry = WhatsAppGeometry()
        val query = NodeQuery(
            listOf(node(id = "root", left = 0, top = 0, right = 1080, bottom = 2340))
        )

        assertEquals(2214, geometry.appBottom(query, metrics(height = 2340, navTop = 2214)))
    }

    @Test fun overlayBottomLimit_followsWhatsAppWhenNavigationBarIsHidden() {
        val geometry = WhatsAppGeometry()
        val query = NodeQuery(
            listOf(node(id = "root", left = 0, top = 0, right = 1080, bottom = 2340))
        )

        assertEquals(2340, geometry.overlayBottomLimit(query, metrics(height = 2340, navTop = null)))
    }
}
