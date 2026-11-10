package com.jk.tvreminder.core

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReminderEngineTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val base = Instant.parse("2026-11-04T02:00:00Z").toEpochMilli()
    private fun time(ms: Long = 0, wall: Long = base + ms) = Moment(ms, wall, zone)
    private fun engine(used: Long = 0) = ReminderEngine(time(), Snapshot(time().date, used)).also { it.update(time(), true) }

    @Test fun defaultWarningsAndConfirmation() {
        val e = engine()
        assertNull(e.update(time(779999), true).reminder)
        assertEquals(Reminder.WARNING, e.update(time(780000), true).reminder)
        assertEquals(5, e.update(time(780000), true).countdown)
        assertEquals(1, e.update(time(784001), true).countdown)
        assertNull(e.update(time(785000), true).reminder)
        assertEquals(Reminder.WARNING, e.update(time(840000), true).reminder)
        e.update(time(845000), true)
        assertEquals(Reminder.CONFIRM, e.update(time(900000), true).reminder)
    }

    @Test fun customThresholds() {
        val e = ReminderEngine(time())
        val c = ReminderConfig(warningMinute = 20, confirmMinute = 25)
        e.update(time(), true, c)
        assertNull(e.update(time(19 * 60000), true, c).reminder)
        for (m in 20..24) {
            assertEquals(Reminder.WARNING, e.update(time(m * 60000L), true, c).reminder)
            e.update(time(m * 60000L + 5000), true, c)
        }
        assertEquals(Reminder.CONFIRM, e.update(time(25 * 60000L), true, c).reminder)
    }

    @Test fun pausePreservesProgressAndDoesNotCountBackground() {
        val e = engine()
        e.update(time(600000), false)
        assertEquals(600000, e.update(time(1800000), true).usedMs)
        assertEquals(Reminder.WARNING, e.update(time(1980000), true).reminder)
    }

    @Test fun switchOffHidesReminderAndStopsCounting() {
        val e = engine()
        e.update(time(900000), true)
        val disabled = ReminderConfig(enabled = false)
        assertNull(e.update(time(900000), true, disabled).reminder)
        assertEquals(900000, e.update(time(1200000), true, disabled).usedMs)
        assertEquals(Reminder.CONFIRM, e.update(time(1200000), true).reminder)
    }

    @Test fun openConfirmationDoesNotQueueMissedMinutes() {
        val e = engine()
        e.update(time(900000), true)
        assertEquals(Reminder.CONFIRM, e.update(time(1080000), true).reminder)
        e.confirm()
        assertNull(e.update(time(1080001), true).reminder)
        assertEquals(Reminder.CONFIRM, e.update(time(1140000), true).reminder)
    }

    @Test fun leavingHidesAndReenteringOverLimitShowsConfirmation() {
        val e = engine()
        e.update(time(900000), true)
        assertNull(e.update(time(901000), false).reminder)
        assertEquals(Reminder.CONFIRM, e.update(time(902000), true).reminder)
        assertEquals(901000, e.snapshot().usedMs)
    }

    @Test fun restartingRestoresOnlySavedUsage() {
        val e = engine()
        e.update(time(600000), false)
        val restarted = ReminderEngine(time(3000000), e.snapshot())
        assertEquals(600000, restarted.update(time(3000000), true).usedMs)
        assertEquals(660000, restarted.update(time(3060000), true).usedMs)
    }

    @Test fun yesterdaySnapshotResets() {
        val e = ReminderEngine(time(), Snapshot("2026-11-03", 900000))
        assertEquals(0, e.update(time(), true).usedMs)
    }

    @Test fun midnightSplitsActiveInterval() {
        val start = Instant.parse("2026-11-04T15:59:58Z").toEpochMilli()
        val e = ReminderEngine(time(0, start), Snapshot("2026-11-04", 900000))
        e.update(time(0, start), true)
        val s = e.update(time(5000, start + 5000), true)
        assertEquals(3000, s.usedMs)
        assertNull(s.reminder)
        assertEquals("2026-11-05", e.snapshot().date)
    }

    @Test fun sameDayWallClockChangesDoNotAlterUsage() {
        val e = engine()
        assertEquals(1000, e.update(time(1000, base + 3600000), true).usedMs)
        assertEquals(2000, e.update(time(2000, base), true).usedMs)
    }

    @Test fun changingThresholdsAppliesToExistingUsage() {
        val e = engine(600000)
        assertEquals(Reminder.CONFIRM, e.update(time(), true, ReminderConfig(true, 5, 10)).reminder)
        assertNull(e.update(time(), true, ReminderConfig(true, 20, 25)).reminder)
    }

    @Test fun returnWithinWarningMinuteDoesNotRepeatWarning() {
        val e = engine()
        e.update(time(780000), true)
        e.update(time(781000), false)
        assertNull(e.update(time(782000), true).reminder)
        assertEquals(Reminder.WARNING, e.update(time(841000), true).reminder)
    }

    @Test fun invalidThresholdsAreRejected() {
        for ((a, b) in listOf(0 to 15, 13 to 13, 16 to 15)) {
            assertThrows(IllegalArgumentException::class.java) { ReminderConfig(true, a, b) }
        }
    }

    @Test fun powerOffBeforeAnyReminderResetsUsage() {
        val e = engine()
        e.update(time(600000), true)
        assertEquals(0L, e.powerOff(time(600001)).usedMs)
        assertEquals(0L, e.update(time(900000), true).usedMs)
        assertEquals(1000L, e.update(time(901000), true).usedMs)
    }

    @Test fun powerOffClearsConfirmationAndResetsNextReminderSchedule() {
        val e = engine()
        e.update(time(900000), true)
        val state = e.powerOff(time(901000))
        assertNull(state.reminder)
        assertEquals(0L, state.usedMs)
        e.update(time(1000000), true)
        assertNull(e.update(time(1779999), true).reminder)
        assertEquals(Reminder.WARNING, e.update(time(1780000), true).reminder)
    }

    @Test fun powerOffWhileDisabledStillResetsSavedUsage() {
        val e = engine(600000)
        val disabled = ReminderConfig(enabled = false)
        e.update(time(), false, disabled)
        assertEquals(0L, e.powerOff(time(1000), disabled).usedMs)
    }

    @Test fun sameBootServiceRestartKeepsProgress() {
        val now = time().copy(bootCount = 10)
        val e = ReminderEngine(now, Snapshot(now.date, 600000, 10))
        assertEquals(600000L, e.update(now, true).usedMs)
        assertEquals(10, e.snapshot().bootCount)
    }

    @Test fun newBootResetsUsageEvenWithoutShutdownEvent() {
        val now = time().copy(bootCount = 11)
        val e = ReminderEngine(now, Snapshot(now.date, 600000, 10))
        assertEquals(0L, e.update(now, true).usedMs)
        assertEquals(11, e.snapshot().bootCount)
    }

    @Test fun olderSnapshotWithoutBootCountDoesNotPretendARebootOccurred() {
        val now = time().copy(bootCount = 11)
        val e = ReminderEngine(now, Snapshot(now.date, 600000))
        assertEquals(600000L, e.update(now, true).usedMs)
        assertEquals(11, e.snapshot().bootCount)
    }

    @Test fun missingBootCountDoesNotMistakeServiceRestartForPowerOff() {
        val e = ReminderEngine(time(), Snapshot(time().date, 600000, 10))
        assertEquals(600000L, e.update(time(), true).usedMs)
    }

    @Test fun repeatedPowerOffDoesNotAccumulateSleepTime() {
        val e = engine(600000)
        e.powerOff(time(1000))
        assertEquals(0L, e.powerOff(time(2000000)).usedMs)
        assertEquals(0L, e.snapshot().usedMs)
    }
}
