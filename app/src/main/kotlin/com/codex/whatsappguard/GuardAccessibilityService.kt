package com.codex.whatsappguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowManager
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

//noinspection AccessibilityPolicy
class GuardAccessibilityService : AccessibilityService() {
    private val whatsappPackages = setOf("com.whatsapp", "com.whatsapp.w4b")
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var overlayBlocker: OverlayBlocker
    private val settleRefreshDelaysMs = longArrayOf(16L, 48L, 120L)
    private var refreshQueued = false
    private var settleRefreshesRemaining = 0

    private val refreshRunnable = Runnable {
        refreshQueued = false
        refreshOverlay()
    }

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 0L
        }
        overlayBlocker = OverlayBlocker(this)
        scheduleRefresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString()
        if (packageName == this.packageName) return

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            scheduleRefresh()
        } else if (packageName in whatsappPackages) {
            scheduleRefresh()
        }
    }

    override fun onInterrupt() {
        mainHandler.removeCallbacks(refreshRunnable)
        refreshQueued = false
        if (::overlayBlocker.isInitialized) overlayBlocker.clear()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable)
        refreshQueued = false
        if (::overlayBlocker.isInitialized) overlayBlocker.clear()
        super.onDestroy()
    }

    private fun scheduleRefresh() {
        settleRefreshesRemaining = settleRefreshDelaysMs.size

        mainHandler.removeCallbacks(refreshRunnable)
        refreshQueued = true
        mainHandler.post(refreshRunnable)
    }

    private fun refreshOverlay() {
        if (!::overlayBlocker.isInitialized) return

        val nodes = collectWhatsAppNodes() ?: run {
            overlayBlocker.clear()
            scheduleSettlingRefresh()
            return
        }

        val screen = screenBounds()
        overlayBlocker.show(buildBlocks(nodes, screen))
        scheduleSettlingRefresh()
    }

    private fun scheduleSettlingRefresh() {
        if (settleRefreshesRemaining <= 0 || refreshQueued) return

        val delayIndex = settleRefreshDelaysMs.size - settleRefreshesRemaining
        settleRefreshesRemaining -= 1
        refreshQueued = true
        mainHandler.postDelayed(refreshRunnable, settleRefreshDelaysMs[delayIndex])
    }

    private fun collectWhatsAppNodes(): List<NodeSnapshot>? {
        val roots = buildList {
            windows.forEach { window ->
                val root = window.root ?: return@forEach
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                add(RootCandidate(root, false, bounds))
            }
            rootInActiveWindow?.let {
                val bounds = Rect()
                it.getBoundsInScreen(bounds)
                add(RootCandidate(it, true, bounds))
            }
        }.filter { it.root.packageName?.toString() in whatsappPackages }

        return roots
            .asSequence()
            .map { RootSnapshot(collectNodes(it.root), it.isActive, it.windowBounds) }
            .filter { it.nodes.isNotEmpty() }
            .maxWithOrNull(
                compareBy<RootSnapshot> { rootScore(it.nodes) + if (it.isActive) 100_000 else 0 }
                    .thenBy { -it.windowBounds.bottom }
                    .thenBy { -candidateBottom(it.nodes) }
                    .thenBy { it.nodes.size }
                    .thenBy { it.nodes.maxOfOrNull { node -> node.rect.height() * node.rect.width() } ?: 0 }
            )?.nodes
    }

    private fun candidateBottom(nodes: List<NodeSnapshot>): Int {
        return nodes.maxOfOrNull { it.rect.bottom } ?: 0
    }

    private fun rootScore(nodes: List<NodeSnapshot>): Int {
        var score = 0
        if (hasId(nodes, "bottom_nav_container")) score += 10_000
        if (hasId(nodes, "voice_note_btn")) score += 10_000
        if (hasId(nodes, "menuitem_overflow")) score += 1_000
        if (hasId(nodes, "menuitem_camera")) score += 500
        if (hasId(nodes, "extended_mini_fab")) score += 250
        if (hasId(nodes, "footer")) score += 250
        return score
    }

    private fun buildBlocks(nodes: List<NodeSnapshot>, screen: Rect): List<BlockSpec> {
        val dark = isDarkMode()
        val pageBg = if (dark) Color.rgb(11, 20, 26) else Color.rgb(255, 255, 255)
        val inputBg = if (dark) Color.rgb(31, 44, 52) else Color.rgb(240, 242, 245)
        val blocks = linkedMapOf<String, BlockSpec>()

        if (looksLikeChatInfoScreen(nodes)) return emptyList()

        findById(nodes, "menuitem_overflow")?.let {
            blocks["top_more"] = BlockSpec("top_more", it.rect.clamp(screen), pageBg)
        } ?: findTopRightNode(nodes, screen, moreLabels())?.let {
            blocks["top_more"] = BlockSpec("top_more", it.rect.expandPx(6, screen), pageBg)
        }

        if (looksLikeMainScreen(nodes, screen)) {
            findById(nodes, "menuitem_camera")?.let {
                blocks["top_camera"] = BlockSpec("top_camera", it.rect.clamp(screen), pageBg)
            } ?: findTopRightNode(nodes, screen, cameraLabels())?.let {
                blocks["top_camera"] = BlockSpec("top_camera", it.rect.expandPx(6, screen), pageBg)
            }

            bottomNavRects(nodes, screen)?.let {
                blocks["bottom_nav_touch"] = BlockSpec("bottom_nav_touch", it.touch.clamp(screen), Color.TRANSPARENT)
                blocks["bottom_nav"] = BlockSpec("bottom_nav", it.visual.clamp(screen), pageBg)
            }

            findMetaAiNode(nodes, screen)?.let {
                blocks["meta_ai"] = BlockSpec("meta_ai", it.rect.expandPx(6, screen), pageBg, dp(22).toFloat())
            } ?: run {
                // WhatsApp sometimes exposes Meta AI poorly. This fallback targets the usual floating button slot.
                val appBottom = appBottom(nodes, screen)
                val size = dp(52)
                val right = screen.right - dp(24)
                val bottom = appBottom - dp(148)
                blocks["meta_ai_fallback"] = BlockSpec(
                    "meta_ai_fallback",
                    Rect(right - size, bottom - size, right, bottom),
                    pageBg,
                    dp(18).toFloat()
                )
            }
        }

        findMicNode(nodes, screen)?.let {
            val rect = it.rect.expandPx(4, screen)
            blocks["voice_mic"] = BlockSpec("voice_mic", rect, inputBg, (rect.width() / 2f))
        }

        return blocks.values.toList()
    }

    private fun looksLikeMainScreen(nodes: List<NodeSnapshot>, screen: Rect): Boolean {
        return hasId(nodes, "bottom_nav_container") ||
            hasId(nodes, "bottom_nav") ||
            looksLikeHomeShell(nodes, screen)
    }

    private fun looksLikeChatInfoScreen(nodes: List<NodeSnapshot>): Boolean {
        return hasId(nodes, "contact_details_card") ||
            hasId(nodes, "top_info_card") ||
            hasId(nodes, "chat_info_media_card_view") ||
            hasId(nodes, "contact_info_security_card") ||
            hasId(nodes, "block_and_report_contact_card")
    }

    private fun bottomNavRects(nodes: List<NodeSnapshot>, screen: Rect): BottomNavRects? {
        val bottomLimit = min(
            appAreaBottom(nodes, screen),
            navigationBarTop(screen) ?: screen.bottom
        )

        findById(nodes, "bottom_nav_container")?.let {
            return bottomNavRectsFor(it.rect.withBottomPadding(dp(4), screen, bottomLimit), screen)
        }
        findById(nodes, "bottom_nav")?.let {
            return bottomNavRectsFor(it.rect.withBottomPadding(dp(4), screen, bottomLimit), screen)
        }
        bottomNavByLabelsRect(nodes, screen, bottomLimit)?.let { return bottomNavRectsFor(it, screen) }

        return if (looksLikeHomeShell(nodes, screen)) {
            val visibleBottom = visibleAppBottom(nodes, screen) ?: return null
            val top = max(screen.top, visibleBottom)
            val bottom = min(bottomLimit, visibleBottom + dp(72))
            if (bottom > top) bottomNavRectsFor(Rect(0, top, screen.right, bottom), screen) else null
        } else {
            null
        }
    }

    private fun bottomNavRectsFor(touch: Rect, screen: Rect): BottomNavRects {
        val visualTop = min(touch.bottom, touch.top + dp(12))
        return BottomNavRects(
            touch = touch,
            visual = Rect(screen.left, visualTop, screen.right, touch.bottom)
        )
    }

    private fun bottomNavByLabelsRect(
        nodes: List<NodeSnapshot>,
        screen: Rect,
        bottomLimit: Int
    ): Rect? {
        val candidates = nodes
            .asSequence()
            .filter { it.matchesBottomNavLabel() }
            .filter { it.rect.centerY() > screen.height() * 0.72f }
            .filter { it.rect.width() <= screen.width() && it.rect.height() <= dp(220) }
            .toList()

        if (candidates.size < 2) return null

        val top = candidates.minOf {
            if (it.rect.height() > dp(44)) it.rect.top else it.rect.top - dp(48)
        }
        val bottom = candidates.maxOf { it.rect.bottom } + dp(24)

        return Rect(
            screen.left,
            max(screen.top, top),
            screen.right,
            min(bottomLimit, bottom)
        )
    }

    private fun NodeSnapshot.matchesBottomNavLabel(): Boolean {
        val values = listOf(text, description).map { normalize(it) }.filter { it.isNotBlank() }
        val labels = bottomNavLabels().map { normalize(it) }
        return values.any { value ->
            labels.any { label ->
                value == label || value.startsWith("$label,")
            }
        }
    }

    private fun looksLikeHomeShell(nodes: List<NodeSnapshot>, screen: Rect): Boolean {
        val hasMainSearch = nodes.any {
            it.textOrDescriptionMatchesAny(searchLabels()) && it.rect.centerY() < screen.height() * 0.3f
        }
        val hasHomeContainer = hasId(nodes, "conversation_list_view_host") ||
            hasId(nodes, "pager") ||
            hasId(nodes, "updates_list") ||
            hasId(nodes, "conversations_coordinator_layout")
        val hasHomeTitle = nodes.any {
            it.textOrDescriptionMatchesAny(listOf("whatsapp", "aggiornamenti", "updates")) &&
                it.rect.centerY() < screen.height() * 0.18f
        }
        val hasLockedChats = nodes.any {
            it.textOrDescriptionMatchesAny(listOf("locked chats", "chat bloccate", "archiviate"))
        }
        return hasHomeContainer || hasMainSearch || hasHomeTitle || hasLockedChats
    }

    private fun findTopRightNode(
        nodes: List<NodeSnapshot>,
        screen: Rect,
        labels: List<String>
    ): NodeSnapshot? {
        return nodes
            .asSequence()
            .filter { it.matchesAny(labels) }
            .filter { it.rect.centerX() > screen.right * 0.58f && it.rect.centerY() < screen.top + dp(210) }
            .filter { it.rect.width() <= dp(120) && it.rect.height() <= dp(120) }
            .maxByOrNull { it.rect.centerX() }
    }

    private fun findMetaAiNode(nodes: List<NodeSnapshot>, screen: Rect): NodeSnapshot? {
        findById(nodes, "extended_mini_fab")?.let { return it }

        return nodes
            .asSequence()
            .filter {
                it.matchesAny(
                    listOf(
                        "meta ai",
                        "assistente",
                        "invia un messaggio all'assistente"
                    )
                )
            }
            .filter { it.rect.centerX() > screen.right * 0.55f && it.rect.centerY() > appBottom(nodes, screen) * 0.35f }
            .filter { it.rect.width() <= dp(140) && it.rect.height() <= dp(140) }
            .maxByOrNull { it.rect.centerY() }
    }

    private fun findMicNode(nodes: List<NodeSnapshot>, screen: Rect): NodeSnapshot? {
        if (!isMessageEntryEmpty(nodes)) return null

        findById(nodes, "voice_note_btn")?.takeIf { it.looksLikeVoiceInput() }?.let { return it }
        findById(nodes, "send_container")
            ?.takeIf { !hasId(nodes, "send") && it.isRightFooterButton(screen) }
            ?.let { return it }

        return nodes
            .asSequence()
            .filter { it.looksLikeVoiceInput() }
            .filter { it.rect.centerX() > screen.right * 0.62f }
            .filter { it.rect.width() <= dp(130) && it.rect.height() <= dp(130) }
            .maxByOrNull { it.rect.centerX() + it.rect.centerY() }
    }

    private fun NodeSnapshot.looksLikeVoiceInput(): Boolean {
        val fullHaystack = normalize("$text $description $viewId")
        return hasId("voice_note_btn") || micLabels().any { fullHaystack.contains(normalize(it)) }
    }

    private fun NodeSnapshot.isRightFooterButton(screen: Rect): Boolean {
        return rect.centerX() > screen.right * 0.62f &&
            rect.centerY() > screen.height() * 0.70f &&
            rect.width() <= dp(180) &&
            rect.height() <= dp(180)
    }

    private fun isMessageEntryEmpty(nodes: List<NodeSnapshot>): Boolean {
        val entry = findById(nodes, "entry") ?: return true
        val value = normalize(entry.text)
        return value.isBlank() || messagePlaceholders().any { value == normalize(it) }
    }

    private fun collectNodes(root: AccessibilityNodeInfo): List<NodeSnapshot> {
        val result = mutableListOf<NodeSnapshot>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val visible = !rect.isEmpty && node.isVisibleToUser
            if (visible) {
                result += NodeSnapshot(
                    text = node.text?.toString().orEmpty(),
                    description = node.contentDescription?.toString().orEmpty(),
                    viewId = node.viewIdResourceName.orEmpty(),
                    className = node.className?.toString().orEmpty(),
                    rect = rect
                )
            }
            for (i in 0 until node.childCount) {
                visit(node.getChild(i))
            }
        }
        visit(root)
        return result
    }

    private fun screenBounds(): Rect {
        val windowManager = getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun isDarkMode(): Boolean {
        val mode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun cameraLabels() = listOf("camera", "fotocamera")

    private fun moreLabels() = listOf("more options", "altre opzioni", "opzioni", "menu")

    private fun micLabels() = listOf(
        "voice message",
        "messaggio vocale",
        "registra messaggio vocale",
        "microfono",
        "audio"
    )

    private fun messagePlaceholders() = listOf(
        "message",
        "messaggio",
        "type a message",
        "scrivi un messaggio"
    )

    private fun bottomNavLabels() = listOf(
        "chat",
        "chats",
        "aggiornamenti",
        "updates",
        "community",
        "comunita",
        "comunità",
        "chiamate",
        "calls"
    )

    private fun searchLabels() = listOf(
        "ask meta ai or search",
        "cerca",
        "search",
        "chiedi a meta ai"
    )

    private fun findById(nodes: List<NodeSnapshot>, idSuffix: String): NodeSnapshot? {
        return nodes
            .asSequence()
            .filter { it.hasId(idSuffix) }
            .maxByOrNull { it.rect.width() * it.rect.height() }
    }

    private fun hasId(nodes: List<NodeSnapshot>, idSuffix: String): Boolean {
        return nodes.any { it.hasId(idSuffix) }
    }

    private fun NodeSnapshot.hasId(idSuffix: String): Boolean {
        return viewId.endsWith(":id/$idSuffix") || viewId.endsWith("/$idSuffix")
    }

    private fun NodeSnapshot.matchesAny(labels: List<String>): Boolean {
        val haystack = normalize("$text $description $viewId $className")
        return labels.any { haystack.contains(normalize(it)) }
    }

    private fun NodeSnapshot.textOrDescriptionMatchesAny(labels: List<String>): Boolean {
        val haystack = normalize("$text $description")
        return labels.any { haystack.contains(normalize(it)) }
    }

    private fun normalize(value: String): String {
        val lower = value.lowercase(Locale.ITALIAN)
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
    }

    private fun appBottom(nodes: List<NodeSnapshot>, screen: Rect): Int {
        return nodes
            .asSequence()
            .filter { it.rect.left <= screen.left && it.rect.right >= screen.right }
            .map { it.rect.bottom }
            .filter { it in (screen.height() / 2)..screen.bottom }
            .maxOrNull()
            ?: nodes.maxOfOrNull { it.rect.bottom }?.coerceAtMost(screen.bottom)
            ?: screen.bottom
    }

    private fun visibleAppBottom(nodes: List<NodeSnapshot>, screen: Rect): Int? {
        return nodes
            .asSequence()
            .map { it.rect.bottom }
            .filter { it in (screen.height() / 2)..(screen.bottom - dp(24)) }
            .maxOrNull()
            ?: appBottom(nodes, screen).takeIf { it < screen.bottom - dp(24) }
    }

    private fun appAreaBottom(nodes: List<NodeSnapshot>, screen: Rect): Int {
        return nodes
            .maxOfOrNull { it.rect.bottom }
            ?.coerceIn(screen.top, screen.bottom)
            ?: screen.bottom
    }

    private fun navigationBarTop(screen: Rect): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = getSystemService(WindowManager::class.java)
            val insets = windowManager.currentWindowMetrics.windowInsets
            if (insets.isVisible(WindowInsets.Type.navigationBars())) {
                val bottomInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                if (bottomInset > 0) return screen.bottom - bottomInset
            }
        }

        return appUsableScreenBottom(screen)
    }

    private fun appUsableScreenBottom(screen: Rect): Int? {
        val windowManager = getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)

        return metrics.heightPixels
            .coerceAtMost(screen.bottom)
            .takeIf { it in (screen.height() / 2)..screen.bottom }
    }

    private fun Rect.expandPx(amount: Int, screen: Rect): Rect {
        return Rect(
            max(screen.left, left - amount),
            max(screen.top, top - amount),
            min(screen.right, right + amount),
            min(screen.bottom, bottom + amount)
        )
    }

    private fun Rect.clamp(screen: Rect): Rect {
        return Rect(
            max(screen.left, left),
            max(screen.top, top),
            min(screen.right, right),
            min(screen.bottom, bottom)
        )
    }

    private fun Rect.withBottomPadding(amount: Int, screen: Rect, bottomLimit: Int): Rect {
        return Rect(
            screen.left,
            top,
            screen.right,
            min(bottomLimit, bottom + amount)
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class NodeSnapshot(
        val text: String,
        val description: String,
        val viewId: String,
        val className: String,
        val rect: Rect
    )

    private data class RootCandidate(
        val root: AccessibilityNodeInfo,
        val isActive: Boolean,
        val windowBounds: Rect
    )

    private data class RootSnapshot(
        val nodes: List<NodeSnapshot>,
        val isActive: Boolean,
        val windowBounds: Rect
    )

    private data class BottomNavRects(
        val touch: Rect,
        val visual: Rect
    )

}
