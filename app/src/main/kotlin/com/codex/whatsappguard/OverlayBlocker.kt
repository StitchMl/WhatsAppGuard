package com.codex.whatsappguard

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

data class BlockSpec(
    val key: String,
    val rect: Rect,
    val color: Int,
    val cornerRadiusPx: Float = 0f
)

class OverlayBlocker(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val activeViews = linkedMapOf<String, View>()
    private val activeSpecs = linkedMapOf<String, BlockSpec>()

    fun show(blocks: List<BlockSpec>) {
        val nextKeys = blocks.map { it.key }.toSet()
        val staleKeys = activeViews.keys.filter { it !in nextKeys }
        staleKeys.forEach { key ->
            activeViews.remove(key)?.let { safeRemove(it) }
            activeSpecs.remove(key)
        }

        blocks.forEach { block ->
            val nextBlock = block.snapshot()
            val existing = activeViews[nextBlock.key]
            if (existing == null) {
                val view = BlockingView(service).apply {
                    paint(nextBlock)
                    contentDescription = ""
                }
                activeViews[nextBlock.key] = view
                activeSpecs[nextBlock.key] = nextBlock
                windowManager.addView(view, paramsFor(nextBlock.rect))
            } else {
                val currentBlock = activeSpecs[nextBlock.key]
                if (currentBlock == nextBlock) return@forEach

                if (
                    currentBlock == null ||
                    currentBlock.color != nextBlock.color ||
                    currentBlock.cornerRadiusPx != nextBlock.cornerRadiusPx
                ) {
                    (existing as? BlockingView)?.paint(nextBlock) ?: existing.setBackgroundColor(nextBlock.color)
                }

                if (currentBlock == null || currentBlock.rect != nextBlock.rect) {
                    windowManager.updateViewLayout(existing, paramsFor(nextBlock.rect))
                }
                activeSpecs[nextBlock.key] = nextBlock
            }
        }
    }

    fun clear() {
        activeViews.values.forEach { safeRemove(it) }
        activeViews.clear()
        activeSpecs.clear()
    }

    private fun BlockSpec.snapshot(): BlockSpec {
        return copy(rect = Rect(rect))
    }

    private fun safeRemove(view: View) {
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // Already detached by the system.
        }
    }

    private fun paramsFor(rect: Rect): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            rect.width().coerceAtLeast(1),
            rect.height().coerceAtLeast(1),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.left
            y = rect.top
            alpha = 1f
        }
    }

    private class BlockingView(context: android.content.Context) : View(context) {
        init {
            isClickable = true
            isLongClickable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        fun paint(block: BlockSpec) {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(block.color)
                cornerRadius = block.cornerRadiusPx
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                performClick()
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
