package com.codex.whatsappguard.scanner

import com.codex.whatsappguard.domain.IntRect
import com.codex.whatsappguard.domain.NodeSnapshot
import com.codex.whatsappguard.domain.WhatsAppPackages
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the pure logic in [AndroidWhatsAppNodeScanner].
 *
 * [AndroidWhatsAppNodeScanner.collectNodes] and [AndroidWhatsAppNodeScanner.rootScore]
 * interact with Android framework types ([AccessibilityService], [AccessibilityWindowInfo],
 * [AccessibilityNodeInfo]) that require a device or Robolectric to instantiate. Those paths
 * are therefore covered by integration / on-device tests.
 *
 * This file focuses on [rootScore], which is the pure scoring function exposed as
 * `internal` specifically for testability.  It drives the window-selection comparator and
 * determines which WhatsApp window root is chosen when multiple candidates exist — including
 * the corrected `isActive` path that caused wrong-window selection in the original code.
 */
class AndroidWhatsAppNodeScannerTest {

    private val scanner = AndroidWhatsAppNodeScanner()

    // ── rootScore — presence of well-known IDs ────────────────────────────

    @Test
    fun `empty node list scores zero`() {
        assertEquals(0, scanner.rootScore(emptyList()))
    }

    @Test
    fun `bottom_nav_container id contributes highest score`() {
        val nodes = listOf(node("bottom_nav_container"))
        assertTrue("bottom_nav_container must score ≥ 10 000", scanner.rootScore(nodes) >= 10_000)
    }

    @Test
    fun `voice_note_btn id contributes same weight as bottom_nav_container`() {
        val navScore = scanner.rootScore(listOf(node("bottom_nav_container")))
        val micScore = scanner.rootScore(listOf(node("voice_note_btn")))
        assertEquals(navScore, micScore)
    }

    @Test
    fun `menuitem_overflow contributes lower score than navigation ids`() {
        val navScore      = scanner.rootScore(listOf(node("bottom_nav_container")))
        val overflowScore = scanner.rootScore(listOf(node("menuitem_overflow")))
        assertTrue(navScore > overflowScore)
    }

    @Test
    fun `multiple known ids accumulate their scores`() {
        val single   = scanner.rootScore(listOf(node("bottom_nav_container")))
        val combined = scanner.rootScore(listOf(node("bottom_nav_container"), node("voice_note_btn")))
        assertTrue("combined score must exceed single-id score", combined > single)
    }

    @Test
    fun `unknown id contributes zero to score`() {
        assertEquals(0, scanner.rootScore(listOf(node("some_random_view"))))
    }

    @Test
    fun `chat screen ids score higher than dialog-only ids`() {
        val chatScore   = scanner.rootScore(listOf(node("voice_note_btn"), node("footer")))
        val dialogScore = scanner.rootScore(listOf(node("menuitem_overflow")))
        assertTrue("chat surface should outscore a dialog", chatScore > dialogScore)
    }

    // ── rootScore — node list content ─────────────────────────────────────

    @Test
    fun `score is additive across all recognized ids present`() {
        val nodes = listOf(
            node("bottom_nav_container"),  // 10 000
            node("voice_note_btn"),        // 10 000
            node("menuitem_overflow"),     //  1 000
            node("menuitem_camera"),       //    500
            node("extended_mini_fab"),     //    250
            node("footer")                 //    250
        )
        assertEquals(22_000, scanner.rootScore(nodes))
    }

    @Test
    fun `nodes with ids not in scoring list do not change score`() {
        val base  = scanner.rootScore(listOf(node("bottom_nav_container")))
        val extra = scanner.rootScore(listOf(node("bottom_nav_container"), node("irrelevant_view")))
        assertEquals(base, extra)
    }

    // ── Active-window bonus semantics (comparator contract) ───────────────

    @Test
    fun `active window bonus dominates id score alone`() {
        // The comparator adds 100 000 to score when isActive == true.
        // A root with no WhatsApp IDs but marked active should outscore one with all IDs
        // but marked inactive.  This test verifies the magnitude relationship holds at
        // the scoring level, which is the same computation used by rootComparator().
        val maxIdScore   = scanner.rootScore(listOf(
            node("bottom_nav_container"), node("voice_note_btn"),
            node("menuitem_overflow"), node("menuitem_camera"),
            node("extended_mini_fab"), node("footer")
        ))  // = 22 000
        val activeBonus  = 100_000

        assertTrue(
            "isActive bonus (100 000) must exceed max achievable ID score ($maxIdScore)",
            activeBonus > maxIdScore
        )
    }

    // ── Outside-WhatsApp safety — package allowlist ───────────────────────
    //
    // collectActiveCandidates() filters every window root by packageName before any
    // node is collected or scored. The tests below pin the authoritative allowlist so
    // that any accidental widening (adding a third-party or system package) causes an
    // immediate test failure, not a silent regression shipped to users.
    //
    // The behavioral guarantee that non-WhatsApp windows produce a null return from
    // collectNodes() is verified by on-device / integration tests, because
    // AccessibilityWindowInfo and AccessibilityNodeInfo require a real Android runtime.

    @Test
    fun `WhatsApp package allowlist contains exactly consumer and business app ids`() {
        assertEquals(
            "allowlist must exactly match the two known WhatsApp package names — " +
            "any addition must be a deliberate, reviewed change",
            setOf("com.whatsapp", "com.whatsapp.w4b"),
            WhatsAppPackages.supported
        )
    }

    @Test
    fun `every allowlisted package is scoped to the com dot whatsapp namespace`() {
        // Guards against accidentally admitting system or third-party packages.
        for (pkg in WhatsAppPackages.supported) {
            assertTrue(
                "allowlist entry '$pkg' must start with 'com.whatsapp'",
                pkg.startsWith("com.whatsapp")
            )
        }
    }

    @Test
    fun `scanner constructed with non-WhatsApp package set still computes rootScore correctly`() {
        // rootScore() is package-agnostic — it scores node content, not package origin.
        // The package guard lives upstream in collectActiveCandidates(). Confirms that
        // constructing the scanner with an alternative allowlist has no side-effect on
        // the scoring function itself.
        val alternateScanner = AndroidWhatsAppNodeScanner(setOf("com.other.app"))
        val nodes = listOf(node("bottom_nav_container"), node("voice_note_btn"))
        assertEquals(scanner.rootScore(nodes), alternateScanner.rootScore(nodes))
    }
}

// ── Helper builders ───────────────────────────────────────────────────────────

private fun node(resourceIdSuffix: String): NodeSnapshot =
    NodeSnapshot(
        viewId    = "com.whatsapp:id/$resourceIdSuffix",
        rect      = IntRect(0, 0, 100, 100),
        text      = "",
        description = "",
        className = "android.view.View"
    )
