package com.codex.whatsappguard.domain

import kotlin.math.max
import kotlin.math.min

/**
 * Immutable rectangle model used by pure overlay logic.
 *
 * @param left   Left coordinate in physical pixels.
 * @param top    Top coordinate in physical pixels.
 * @param right  Right coordinate in physical pixels.
 * @param bottom Bottom coordinate in physical pixels.
 */
data class IntRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int  get() = right - left
    val height: Int get() = bottom - top
    @Suppress("unused")
    val isEmpty: Boolean get() = width <= 0 || height <= 0
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val area: Int   get() = max(0, width) * max(0, height)

    fun clamp(bounds: IntRect): IntRect = IntRect(
        left   = max(bounds.left,  left),
        top    = max(bounds.top,   top),
        right  = min(bounds.right, right),
        bottom = min(bounds.bottom, bottom)
    )

    fun expand(amount: Int, bounds: IntRect): IntRect = IntRect(
        left   = max(bounds.left,   left   - amount),
        top    = max(bounds.top,    top    - amount),
        right  = min(bounds.right,  right  + amount),
        bottom = min(bounds.bottom, bottom + amount)
    )

    fun withBottomPadding(padding: Int, bounds: IntRect, bottomLimit: Int): IntRect =
        IntRect(bounds.left, top, bounds.right, min(bottomLimit, bottom + padding))
}
