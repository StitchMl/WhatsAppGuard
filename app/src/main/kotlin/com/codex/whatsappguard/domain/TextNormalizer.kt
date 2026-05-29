package com.codex.whatsappguard.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Normalizes accessibility text for locale-tolerant matching.
 *
 * @param locale Locale used for lowercasing.
 */
class TextNormalizer(private val locale: Locale = Locale.ITALIAN) {
    /** Lowercases, strips accents and trims a nullable value. */
    fun normalize(value: String?): String {
        val lower = value.orEmpty().lowercase(locale)
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
    }
}
