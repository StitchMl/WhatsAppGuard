package com.codex.whatsappguard.service

/**
 * Coalesces accessibility events into one refresh on the main queue.
 *
 * Most changes use a single immediate refresh. System chrome changes are different:
 * Android may notify before WhatsApp finishes relayout, so [scheduleSettlingRefreshes]
 * adds a short bounded follow-up burst after the immediate refresh.
 *
 * Some OEMs also change system chrome without emitting accessibility/display events.
 * [startForegroundWatch] keeps a small foreground-only pulse alive while WhatsApp is
 * visible, so overlay geometry follows silent app-window resizes.
 *
 * @param scheduler Queue used to run refresh work.
 * @param onRefresh Callback invoked once for the next queued refresh.
 * @param settlingDelaysMs Delay list used only for system/window settling refreshes.
 * @param foregroundWatchIntervalMs Poll interval used only while WhatsApp is foreground.
 */
class OverlayRefreshScheduler(
    private val scheduler: TaskScheduler,
    private val settlingDelaysMs: List<Long> = listOf(16L, 48L, 120L),
    private val foregroundWatchIntervalMs: Long = 80L,
    private val onRefresh: () -> Unit
) {
    private var pending = false
    private var watchingForeground = false

    private val refreshRunnable = Runnable {
        pending = false
        onRefresh()
    }

    private val settlingRunnables = settlingDelaysMs.map {
        Runnable { schedule() }
    }

    private val foregroundWatchRunnable: Runnable = Runnable {
        if (!watchingForeground) return@Runnable

        schedule()
        scheduler.postDelayed(foregroundWatchRunnable, foregroundWatchIntervalMs)
    }

    /**
     * Schedules one immediate refresh unless one is already queued.
     */
    fun schedule() {
        if (pending) return

        pending = true
        scheduler.post(refreshRunnable)
    }

    /**
     * Schedules one immediate refresh and a small number of follow-up checks.
     *
     * This is reserved for display/system-window changes where Android reports the
     * transition before third-party app bounds have reached their final position.
     */
    fun scheduleSettlingRefreshes() {
        schedule()
        settlingRunnables.forEachIndexed { index, runnable ->
            scheduler.remove(runnable)
            scheduler.postDelayed(runnable, settlingDelaysMs[index])
        }
    }

    /**
     * Starts a foreground-only pulse that detects silent app-bound changes.
     */
    fun startForegroundWatch() {
        if (watchingForeground) return

        watchingForeground = true
        scheduler.postDelayed(foregroundWatchRunnable, foregroundWatchIntervalMs)
    }

    /**
     * Stops the foreground pulse and removes its next queued tick.
     */
    fun stopForegroundWatch() {
        watchingForeground = false
        scheduler.remove(foregroundWatchRunnable)
    }

    /**
     * Removes the queued refresh, if any.
     */
    fun cancel() {
        scheduler.remove(refreshRunnable)
        settlingRunnables.forEach(scheduler::remove)
        stopForegroundWatch()
        pending = false
    }
}
