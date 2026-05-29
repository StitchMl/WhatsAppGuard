package com.codex.whatsappguard.domain

import com.codex.whatsappguard.metrics
import com.codex.whatsappguard.node
import org.junit.Assert.*
import org.junit.Test

class NodeQueryTest {
    @Test fun hasId_matchesSuffix() {
        val query = NodeQuery(listOf(node(id="menuitem_overflow")))
        assertTrue(query.hasId("menuitem_overflow"))
        assertFalse(query.hasId("other"))
    }

    @Test fun findLargestById_returnsBiggestArea() {
        val small = node(id="entry", left=0, top=0, right=10, bottom=10)
        val large = node(id="entry", left=0, top=0, right=100, bottom=100)
        val query = NodeQuery(listOf(small, large))
        assertEquals(large.rect, query.findLargestById("entry")?.rect)
    }

    @Test fun findTopRightNode_onlyInTopBand() {
        val topRight = node(description="More options", left=900, top=80, right=1000, bottom=160)
        val bottom   = node(description="More options", left=900, top=2000, right=1000, bottom=2080)
        val query    = NodeQuery(listOf(topRight, bottom))
        val found    = query.findTopRightNode(listOf("more options"), metrics())
        assertEquals(topRight.rect, found?.rect)
    }

    @Test fun matchesAny_isLocaleNormalized() {
        val n = node(description="Più opzioni")
        assertTrue(NodeQuery(listOf(n)).matchesAny(n, listOf("piu opzioni")))
    }
}
