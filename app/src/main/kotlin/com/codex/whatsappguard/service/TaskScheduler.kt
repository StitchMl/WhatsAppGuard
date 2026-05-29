package com.codex.whatsappguard.service

/**
 * Abstraction over [android.os.Handler] for scheduling and cancelling deferred work.
 *
 * Extracted to enable deterministic unit testing of [OverlayRefreshScheduler] without
 * an Android Looper.
 */
interface TaskScheduler {

    /**
     * Enqueues [runnable] for immediate execution on the scheduler's thread.
     *
     * @param runnable Work to execute.
     */
    fun post(runnable: Runnable)

    /**
     * Enqueues [runnable] for execution after [delayMs] milliseconds.
     *
     * @param runnable Work to execute.
     * @param delayMs  Delay in milliseconds; must be ≥ 0.
     */
    fun postDelayed(runnable: Runnable, delayMs: Long)

    /**
     * Removes all pending instances of [runnable] from the queue.
     * Safe to call when [runnable] is not queued.
     *
     * @param runnable Previously enqueued work to cancel.
     */
    fun remove(runnable: Runnable)
}
