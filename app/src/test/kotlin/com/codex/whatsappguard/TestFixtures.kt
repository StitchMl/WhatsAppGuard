package com.codex.whatsappguard

import com.codex.whatsappguard.domain.IntRect
import com.codex.whatsappguard.domain.NodeSnapshot
import com.codex.whatsappguard.domain.ScreenMetrics

internal fun rect(left: Int, top: Int, right: Int, bottom: Int) = IntRect(left, top, right, bottom)

internal fun node(
    id: String = "", text: String = "", description: String = "",
    left: Int = 0, top: Int = 0, right: Int = 10, bottom: Int = 10
) = NodeSnapshot(
    text        = text,
    description = description,
    viewId      = if (id.isBlank()) "" else "com.whatsapp:id/$id",
    className   = "android.view.View",
    rect        = rect(left, top, right, bottom)
)

internal fun metrics(
    width: Int = 1080, height: Int = 2400, navTop: Int? = 2280,
    usableBottom: Int? = navTop,
    density: Float = 3f, dark: Boolean = true
) = ScreenMetrics(rect(0, 0, width, height), navTop, density, dark, usableBottom)
