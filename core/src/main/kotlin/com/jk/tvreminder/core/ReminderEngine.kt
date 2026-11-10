package com.jk.tvreminder.core

import java.time.Instant
import java.time.ZoneId

data class ReminderConfig(val enabled: Boolean = true, val warningMinute: Int = 13, val confirmMinute: Int = 15) {
    init {
        require(warningMinute > 0) { "开始提醒必须大于 0 分钟" }
        require(confirmMinute > warningMinute) { "确认提醒时间必须晚于开始提醒时间" }
    }
}
data class Moment(val monotonicMs: Long, val wallMs: Long, val zone: ZoneId, val bootCount: Int = -1) {
    val date: String get() = Instant.ofEpochMilli(wallMs).atZone(zone).toLocalDate().toString()
}
data class Snapshot(val date: String, val usedMs: Long, val bootCount: Int = -1)
enum class Reminder { WARNING, CONFIRM }
data class ReminderState(val usedMs: Long, val reminder: Reminder?, val countdown: Int)

class ReminderEngine(initial: Moment, saved: Snapshot = Snapshot(initial.date, 0)) {
    private var last = initial
    private var date = initial.date
    private val rebooted = initial.bootCount >= 0 && saved.bootCount >= 0 && initial.bootCount != saved.bootCount
    private val bootCount = if (initial.bootCount >= 0) initial.bootCount else saved.bootCount
    private var used = if (saved.date == date && !rebooted) saved.usedMs.coerceAtLeast(0) else 0
    private var active = false
    private var settings = ReminderConfig()
    private var visible: Reminder? = null
    private var shownAt = 0L

    fun update(now: Moment, watching: Boolean, config: ReminderConfig = ReminderConfig()): ReminderState {
        val previousMinute = used / MINUTE
        val newDay = now.date != date
        val delta = (now.monotonicMs - last.monotonicMs).coerceAtLeast(0)
        if (newDay) {
            // Credit only the part after midnight, never a whole background interval.
            val midnight = Instant.ofEpochMilli(now.wallMs).atZone(now.zone)
                .toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli()
            used = if (active && now.wallMs >= last.wallMs) minOf(delta, now.wallMs - midnight) else 0
            date = now.date
            visible = null
        } else if (active) {
            used += delta
        }

        val changed = settings != config
        val nextActive = watching && config.enabled
        val entering = nextActive && !active
        if (!nextActive || changed) visible = null
        if (visible == Reminder.WARNING && now.monotonicMs - shownAt >= WARNING_DURATION) visible = null

        val minute = used / MINUTE
        val crossed = if (newDay) minute > 0 else minute > previousMinute
        if (nextActive && visible == null) {
            val confirmationDue = minute >= config.confirmMinute && (entering || changed || crossed)
            val warningDue = minute >= config.warningMinute && minute < config.confirmMinute && crossed
            if (confirmationDue || warningDue) {
                visible = if (confirmationDue) Reminder.CONFIRM else Reminder.WARNING
                shownAt = now.monotonicMs
            }
        }
        active = nextActive
        settings = config
        last = now
        val remaining = if (visible == Reminder.WARNING) {
            ((WARNING_DURATION - (now.monotonicMs - shownAt) + 999) / 1000).toInt().coerceIn(1, 5)
        } else 0
        return ReminderState(used, visible, remaining)
    }

    fun confirm() { visible = null }
    fun powerOff(now: Moment, config: ReminderConfig = ReminderConfig()): ReminderState {
        update(now, false, config)
        used = 0
        visible = null
        return ReminderState(0, null, 0)
    }

    fun snapshot(): Snapshot = Snapshot(date, used, bootCount)

    private companion object {
        const val MINUTE = 60_000L
        const val WARNING_DURATION = 5_000L
    }
}
