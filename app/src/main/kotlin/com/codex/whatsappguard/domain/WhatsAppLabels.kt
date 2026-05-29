package com.codex.whatsappguard.domain

/** Centralized labels used to recognize WhatsApp controls across locales. */
object WhatsAppLabels {
    val back              = listOf("back", "indietro", "navigate up", "torna indietro")
    val bottomNavigation  = listOf("chat", "chats", "aggiornamenti", "updates",
                                   "community", "comunita", "chiamate", "calls")
    val camera            = listOf("camera", "fotocamera")
    val call              = listOf("voice call", "audio call", "chiama", "chiamata vocale")
    val metaAi            = listOf("meta ai", "assistente", "invia un messaggio all'assistente")
    val microphone        = listOf("voice message", "messaggio vocale",
                                   "registra messaggio vocale", "microfono", "audio")
    val more              = listOf("more options", "altre opzioni", "opzioni", "menu")
    val messagePlaceholders = listOf("message", "messaggio", "type a message", "scrivi un messaggio")
    val search            = listOf("ask meta ai or search", "cerca", "search", "chiedi a meta ai")
    val videoCall         = listOf("video call", "videochiamata", "videochiama")
}

/** Supported WhatsApp application package names. */
object WhatsAppPackages {
    val supported = setOf("com.whatsapp", "com.whatsapp.w4b")
}
