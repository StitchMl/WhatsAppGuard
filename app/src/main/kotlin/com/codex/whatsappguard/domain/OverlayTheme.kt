package com.codex.whatsappguard.domain

/**
 * Color palette used by overlay blocks.
 *
 * @param pageBackground  WhatsApp page background color.
 * @param inputBackground WhatsApp chat input background color.
 * @param transparent     Fully transparent touch-only overlay color.
 */
data class OverlayTheme(
    val pageBackground: Int,
    val inputBackground: Int,
    val transparent: Int = TRANSPARENT
) {
    companion object {
        const val TRANSPARENT: Int = 0x00000000

        /** Creates a WhatsApp-like palette for the current theme. */
        fun fromDarkMode(isDarkMode: Boolean): OverlayTheme = if (isDarkMode) {
            OverlayTheme(pageBackground = rgb(11, 20, 26),  inputBackground = rgb(31, 44, 52))
        } else {
            OverlayTheme(pageBackground = rgb(255, 255, 255), inputBackground = rgb(240, 242, 245))
        }

        private fun rgb(r: Int, g: Int, b: Int): Int =
            -0x1000000 or (r shl 16) or (g shl 8) or b
    }
}
