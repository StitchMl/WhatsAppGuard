package com.codex.whatsappguard.scanner

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.codex.whatsappguard.domain.IntRect
import com.codex.whatsappguard.domain.NodeQuery
import com.codex.whatsappguard.domain.NodeSnapshot
import com.codex.whatsappguard.domain.WhatsAppPackages

/**
 * Converts Android accessibility windows into framework-free WhatsApp node snapshots.
 *
 * The scanner first verifies that WhatsApp owns the foreground root. Once that is true,
 * every visible WhatsApp window can be considered. This keeps overlays away from other
 * apps while avoiding flicker on devices whose active/focused window flags oscillate
 * during keyboard and activity transitions.
 *
 * @param packageNames WhatsApp package names accepted by the scanner.
 */
class AndroidWhatsAppNodeScanner(
    private val packageNames: Set<String> = WhatsAppPackages.supported
) {
    /**
     * Collects nodes from the best currently visible WhatsApp root.
     *
     * @param service Accessibility service that exposes active windows.
     * @return Visible WhatsApp nodes, or null when WhatsApp is not foreground.
     */
    fun collectNodes(service: AccessibilityService): List<NodeSnapshot>? {
        val roots = collectForegroundCandidates(service)
        return roots.asSequence()
            .map { RootSnapshot(collectNodes(it.root), it.isActive, it.windowBounds) }
            .filter { it.nodes.isNotEmpty() }
            .maxWithOrNull(rootComparator())
            ?.nodes
    }

    private fun collectForegroundCandidates(service: AccessibilityService): List<RootCandidate> {
        val roots = mutableListOf<RootCandidate>()
        var hasForegroundWhatsApp = false

        service.windows.forEach { window ->
            val root = window.root ?: return@forEach
            val bounds = Rect().also { window.getBoundsInScreen(it) }.toIntRect()
            val isForeground = window.isActive || window.isFocused
            if (isForeground && root.packageName?.toString() in packageNames) {
                hasForegroundWhatsApp = true
            }
            roots += RootCandidate(root, isForeground, bounds)
        }

        service.rootInActiveWindow?.let { root ->
            val bounds = Rect().also { root.getBoundsInScreen(it) }.toIntRect()
            if (root.packageName?.toString() in packageNames) {
                hasForegroundWhatsApp = true
            }
            roots += RootCandidate(root, isActive = true, windowBounds = bounds)
        }

        if (!hasForegroundWhatsApp) return emptyList()
        return roots.filter { it.root.packageName?.toString() in packageNames }
    }

    private fun collectNodes(root: AccessibilityNodeInfo): List<NodeSnapshot> {
        val result = mutableListOf<NodeSnapshot>()
        fun visit(node: AccessibilityNodeInfo?) {
            node ?: return
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty && node.isVisibleToUser) result += node.toSnapshot(rect)
            for (i in 0 until node.childCount) visit(node.getChild(i))
        }
        visit(root)
        return result
    }

    private fun rootComparator(): Comparator<RootSnapshot> =
        compareBy<RootSnapshot> { rootScore(it.nodes) + if (it.isActive) ACTIVE_ROOT_BONUS else 0 }
            .thenBy { it.windowBounds.bottom }
            .thenBy { candidateBottom(it.nodes) }
            .thenBy { it.nodes.size }
            .thenBy { it.nodes.maxOfOrNull { node -> node.rect.area } ?: 0 }

    /**
     * Scores a node list by presence of well-known WhatsApp resource IDs.
     *
     * @param nodes Visible node snapshots.
     * @return Score used to rank candidate WhatsApp windows.
     */
    internal fun rootScore(nodes: List<NodeSnapshot>): Int {
        val query = NodeQuery(nodes)
        var score = 0
        if (query.hasId("bottom_nav_container")) score += 10_000
        if (query.hasId("voice_note_btn")) score += 10_000
        if (query.hasId("menuitem_overflow")) score += 1_000
        if (query.hasId("menuitem_camera")) score += 500
        if (query.hasId("extended_mini_fab")) score += 250
        if (query.hasId("footer")) score += 250
        return score
    }

    private fun candidateBottom(nodes: List<NodeSnapshot>): Int =
        nodes.maxOfOrNull { it.rect.bottom } ?: 0

    private fun AccessibilityNodeInfo.toSnapshot(rect: Rect) = NodeSnapshot(
        text = text?.toString().orEmpty(),
        description = contentDescription?.toString().orEmpty(),
        viewId = viewIdResourceName.orEmpty(),
        className = className?.toString().orEmpty(),
        rect = rect.toIntRect()
    )

    private fun Rect.toIntRect(): IntRect = IntRect(left, top, right, bottom)

    private data class RootCandidate(
        val root: AccessibilityNodeInfo,
        val isActive: Boolean,
        val windowBounds: IntRect
    )

    private data class RootSnapshot(
        val nodes: List<NodeSnapshot>,
        val isActive: Boolean,
        val windowBounds: IntRect
    )

    private companion object {
        const val ACTIVE_ROOT_BONUS = 100_000
    }
}
