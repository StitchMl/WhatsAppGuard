package com.codex.whatsappguard.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.codex.whatsappguard.domain.IntRect
import com.codex.whatsappguard.domain.OverlayBlock

/**
 * Renders accessibility overlay views that intercept touches on blocked WhatsApp controls.
 *
 * ### Flicker-free update strategy
 * Views are keyed by [OverlayBlock.key]. On each [show] call a minimal diff is applied:
 * - Views absent from the new set are removed.
 * - Existing views are updated in-place (repaint if color/radius changed, relayout if rect
 *   changed). The same [WindowManager.LayoutParams] instance is mutated so Android does not
 *   detach and reattach the backing surface — this is the primary guard against tearing.
 * - New keys trigger a single [WindowManager.addView] call.
 *
 * @param windowManager WindowManager used to add/update/remove views; injected for testing.
 * @param viewFactory Factory that produces overlay views.
 */
class OverlayBlocker(
    private val windowManager: WindowManager,
    private val viewFactory: () -> View
) {
    constructor(context: Context) : this(
        windowManager = context.getSystemService(WindowManager::class.java),
        viewFactory = { BlockingView(context) }
    )

    private val activeViews  = linkedMapOf<String, View>()
    private val activeBlocks = linkedMapOf<String, OverlayBlock>()
    // Cached per-view params — mutated in-place to avoid surface detach on update.
    private val activeParams = linkedMapOf<String, WindowManager.LayoutParams>()

    /**
     * Synchronizes active overlay views with the requested block set.
     * Stale blocks are removed; new or changed blocks are upserted — all in one pass.
     *
     * @param blocks Target block set.
     */
    fun show(blocks: List<OverlayBlock>) {
        removeStaleViews(blocks.map { it.key }.toSet())
        blocks.forEach(::upsertView)
    }

    /** Removes every active overlay view and clears all internal state. */
    fun clear() {
        activeViews.values.forEach(::safeRemove)
        activeViews.clear()
        activeBlocks.clear()
        activeParams.clear()
    }

    // ── Diff logic ────────────────────────────────────────────────────────

    private fun removeStaleViews(nextKeys: Set<String>) {
        activeViews.keys.filter { it !in nextKeys }.forEach { key ->
            activeViews.remove(key)?.let(::safeRemove)
            activeBlocks.remove(key)
            activeParams.remove(key)
        }
    }

    private fun upsertView(block: OverlayBlock) {
        val existingView = activeViews[block.key]
        if (existingView == null) { addView(block); return }

        val currentBlock = activeBlocks[block.key]
        if (currentBlock == block) return  // Nothing changed — skip all work.

        if (currentBlock == null || currentBlock.requiresRepaint(block)) {
            (existingView as? BlockingView)?.paint(block)
                ?: existingView.setBackgroundColor(block.color)
        }

        if (currentBlock == null || currentBlock.rect != block.rect) {
            // Mutate the cached params instance rather than creating a new object.
            // Reusing the same instance prevents Android from detaching and reattaching
            // the backing surface, which would cause a single-frame visual gap.
            val params = activeParams[block.key]
                ?: paramsFor(block.rect).also { activeParams[block.key] = it }
            applyRectToParams(params, block.rect)
            windowManager.updateViewLayout(existingView, params)
        }

        activeBlocks[block.key] = block
    }

    private fun addView(block: OverlayBlock) {
        val view = viewFactory().apply { contentDescription = "" }
        (view as? BlockingView)?.paint(block) ?: view.setBackgroundColor(block.color)

        val params = paramsFor(block.rect)
        activeViews[block.key]  = view
        activeBlocks[block.key] = block
        activeParams[block.key] = params
        windowManager.addView(view, params)
    }

    // ── WindowManager helpers ─────────────────────────────────────────────

    private fun OverlayBlock.requiresRepaint(next: OverlayBlock): Boolean =
        color != next.color || cornerRadiusPx != next.cornerRadiusPx

    private fun safeRemove(view: View) {
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // Android can silently detach accessibility overlays during window
            // transitions. The view is already gone from the WM — ignore.
        }
    }

    private fun paramsFor(rect: IntRect): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            rect.width.coerceAtLeast(1),
            rect.height.coerceAtLeast(1),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE        or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                // Hardware acceleration avoids software-rasterisation stutter on the
                // overlay surface, especially visible on mid-range devices at 90+ Hz.
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x       = rect.left
            y       = rect.top
            alpha   = 1f
        }

    private fun applyRectToParams(params: WindowManager.LayoutParams, rect: IntRect) {
        params.width  = rect.width.coerceAtLeast(1)
        params.height = rect.height.coerceAtLeast(1)
        params.x      = rect.left
        params.y      = rect.top
    }

    // ── Inner view ────────────────────────────────────────────────────────

    private class BlockingView(context: Context) : View(context) {
        init {
            isClickable      = true
            isLongClickable  = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        fun paint(block: OverlayBlock) {
            background = GradientDrawable().apply {
                setColor(block.color)
                cornerRadius = block.cornerRadiusPx
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) performClick()
            return true
        }

        override fun performClick(): Boolean { super.performClick(); return true }
    }
}
