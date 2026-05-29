package com.codex.whatsappguard.service

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayRefreshSchedulerTest {
    @Test
    fun schedulePostsOneImmediateRefresh() {
        val fake = FakeTaskScheduler()
        val scheduler = OverlayRefreshScheduler(fake) {}

        scheduler.schedule()

        assertEquals(1, fake.pendingCount())
        assertEquals(0L, fake.firstDelay())
    }

    @Test
    fun repeatedScheduleCallsCoalesceWhilePending() {
        val fake = FakeTaskScheduler()
        var refreshCount = 0
        val scheduler = OverlayRefreshScheduler(fake) { refreshCount++ }

        repeat(20) { scheduler.schedule() }
        fake.runAll()

        assertEquals(1, refreshCount)
    }

    @Test
    fun scheduleAfterRefreshQueuesAnotherRefresh() {
        val fake = FakeTaskScheduler()
        var refreshCount = 0
        val scheduler = OverlayRefreshScheduler(fake) { refreshCount++ }

        scheduler.schedule()
        fake.runAll()
        scheduler.schedule()
        fake.runAll()

        assertEquals(2, refreshCount)
    }

    @Test
    fun settlingRefreshesQueueImmediateAndBoundedFollowUps() {
        val fake = FakeTaskScheduler()
        val scheduler = OverlayRefreshScheduler(fake, settlingDelaysMs = listOf(80L, 180L)) {}

        scheduler.scheduleSettlingRefreshes()

        assertEquals(listOf(0L, 80L, 180L), fake.delays())
    }

    @Test
    fun repeatedSettlingRefreshesReplacePendingFollowUps() {
        val fake = FakeTaskScheduler()
        val scheduler = OverlayRefreshScheduler(fake, settlingDelaysMs = listOf(80L, 180L)) {}

        scheduler.scheduleSettlingRefreshes()
        scheduler.scheduleSettlingRefreshes()

        assertEquals(listOf(0L, 80L, 180L), fake.delays())
    }

    @Test
    fun settlingFollowUpsRunAdditionalRefreshes() {
        val fake = FakeTaskScheduler()
        var refreshCount = 0
        val scheduler = OverlayRefreshScheduler(fake, settlingDelaysMs = listOf(80L, 180L)) {
            refreshCount++
        }

        scheduler.scheduleSettlingRefreshes()
        fake.runAll()

        assertEquals(3, refreshCount)
    }

    @Test
    fun foregroundWatchQueuesOneDelayedTick() {
        val fake = FakeTaskScheduler()
        val scheduler = OverlayRefreshScheduler(fake, foregroundWatchIntervalMs = 200L) {}

        scheduler.startForegroundWatch()
        scheduler.startForegroundWatch()

        assertEquals(listOf(200L), fake.delays())
    }

    @Test
    fun foregroundWatchTickSchedulesRefreshAndQueuesNextTick() {
        val fake = FakeTaskScheduler()
        var refreshCount = 0
        val scheduler = OverlayRefreshScheduler(fake, foregroundWatchIntervalMs = 200L) {
            refreshCount++
        }

        scheduler.startForegroundWatch()
        fake.runNext()
        fake.runNext()

        assertEquals(1, refreshCount)
        assertEquals(listOf(200L), fake.delays())
    }

    @Test
    fun stopForegroundWatchRemovesPendingTick() {
        val fake = FakeTaskScheduler()
        val scheduler = OverlayRefreshScheduler(fake, foregroundWatchIntervalMs = 200L) {}

        scheduler.startForegroundWatch()
        scheduler.stopForegroundWatch()

        assertEquals(0, fake.pendingCount())
    }

    @Test
    fun cancelRemovesPendingRefresh() {
        val fake = FakeTaskScheduler()
        var refreshCount = 0
        val scheduler = OverlayRefreshScheduler(fake) { refreshCount++ }

        scheduler.schedule()
        scheduler.cancel()
        fake.runAll()

        assertEquals(0, refreshCount)
        assertEquals(0, fake.pendingCount())
    }
}

private class FakeTaskScheduler : TaskScheduler {
    private data class ScheduledTask(val runnable: Runnable, val delayMs: Long)

    private val pending = mutableListOf<ScheduledTask>()

    override fun post(runnable: Runnable) {
        pending += ScheduledTask(runnable, 0L)
    }

    override fun postDelayed(runnable: Runnable, delayMs: Long) {
        pending += ScheduledTask(runnable, delayMs)
    }

    override fun remove(runnable: Runnable) {
        pending.removeAll { it.runnable === runnable }
    }

    fun pendingCount(): Int = pending.size

    fun firstDelay(): Long = pending.first().delayMs

    fun delays(): List<Long> = pending.map { it.delayMs }

    fun runNext() {
        val next = pending.minBy { it.delayMs }
        pending.remove(next)
        next.runnable.run()
    }

    fun runAll() {
        while (pending.isNotEmpty()) {
            runNext()
        }
    }
}
