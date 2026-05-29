package com.codex.whatsappguard.service

import android.os.Handler

/**
 * Production [TaskScheduler] backed by an Android [Handler].
 *
 * @param handler Handler whose Looper receives the scheduled work.
 */
class HandlerTaskScheduler(private val handler: Handler) : TaskScheduler {

    override fun post(runnable: Runnable) {
        handler.post(runnable)
    }

    override fun postDelayed(runnable: Runnable, delayMs: Long) {
        handler.postDelayed(runnable, delayMs)
    }

    override fun remove(runnable: Runnable) {
        handler.removeCallbacks(runnable)
    }
}
