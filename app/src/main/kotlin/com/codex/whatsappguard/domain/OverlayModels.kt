package com.codex.whatsappguard.domain

/**
 * Visible accessibility node snapshot detached from Android framework objects.
 */
data class NodeSnapshot(
    val text: String = "",
    val description: String = "",
    val viewId: String = "",
    val className: String = "",
    val rect: IntRect
)

/**
 * Screen metrics required to calculate cross-device overlays.
 *
 * @param bounds           Full real screen bounds in physical pixels.
 * @param navigationBarTop Top coordinate of the navigation bar, when visible.
 * @param usableScreenBottom Bottom coordinate currently reported as usable by Android.
 * @param density          Display density used for dp-to-pixel conversion.
 * @param isDarkMode       True when Android is currently in night mode.
 */
data class ScreenMetrics(
    val bounds: IntRect,
    val navigationBarTop: Int?,
    val density: Float,
    val isDarkMode: Boolean,
    val usableScreenBottom: Int? = navigationBarTop
) {
    /** Converts density-independent pixels to physical pixels. */
    fun dp(value: Int): Int = (value * density).toInt()
}

/**
 * Overlay rectangle to render and use as a touch blocker.
 *
 * @param key             Stable identity used to update existing overlay views.
 * @param rect            Screen rectangle in physical pixels.
 * @param color           ARGB color; transparent still intercepts touches.
 * @param cornerRadiusPx  Optional corner radius for rounded blocks.
 */
data class OverlayBlock(
    val key: String,
    val rect: IntRect,
    val color: Int,
    val cornerRadiusPx: Float = 0f
)

/**
 * Request passed to the pure WhatsApp overlay builder.
 */
data class OverlayBuildRequest(
    val nodes: List<NodeSnapshot>,
    val metrics: ScreenMetrics
)
