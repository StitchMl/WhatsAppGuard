package com.codex.whatsappguard.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {
    private val normalizer = TextNormalizer()

    @Test fun stripsAccents() { assertEquals("piu", normalizer.normalize("Più")) }
    @Test fun lowercases()    { assertEquals("hello", normalizer.normalize("HELLO")) }
    @Test fun trimsWhitespace() { assertEquals("ok", normalizer.normalize("  OK  ")) }
    @Test fun handlesNull()   { assertEquals("", normalizer.normalize(null)) }
}
