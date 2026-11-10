package com.jk.tvreminder

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.jk.tvreminder.core.Moment
import com.jk.tvreminder.core.ReminderEngine
import com.jk.tvreminder.core.Snapshot
import java.time.ZoneId

class ReminderService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: ReminderPreferences
    private lateinit var engine: ReminderEngine
    private lateinit var overlay: ReminderOverlay
    private lateinit var detector: ForegroundDetector
    private var running = false
    private var screenOff = false
    private var watching = false
    private var lastSaved = 0L
    private var target = ""
    private var bootCount = -1
    private val eventRefresh = Runnable { if (running) refresh() }
    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            refresh()
            handler.postDelayed(this, 250)
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_SHUTDOWN -> screenOff = true
                Intent.ACTION_SCREEN_ON -> screenOff = false
            }
            refresh()
        }
    }

    override fun onServiceConnected() {
        if (running) return
        store = ReminderPreferences(this)
        // The setting is absent on API 23; normal shutdown/screen-off still resets there.
        bootCount = try { Settings.Global.getInt(contentResolver, "boot_count", -1) }
            catch (_: SecurityException) { -1 }
        screenOff = !getSystemService(PowerManager::class.java).isInteractive
        val now = moment()
        engine = ReminderEngine(now, store.snapshot(now.date))
        if (screenOff) engine.powerOff(now, store.config())
        save(now.monotonicMs, durable = true)
        target = store.targetPackage
        detector = ForegroundDetector(this)
        overlay = ReminderOverlay(this) {
            // Settle elapsed time first so a click exactly on a minute skips that boundary.
            refresh()
            engine.confirm()
            overlay.hide()
        }
        store.preferences.registerOnSharedPreferenceChangeListener(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SHUTDOWN)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        else { @Suppress("DEPRECATION") registerReceiver(screenReceiver, filter) }
        running = true
        connected = true
        problem = null
        handler.post(poll)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!running) return
        handler.removeCallbacks(eventRefresh)
        handler.post(eventRefresh)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (!running || key !in ReminderPreferences.SETTING_KEYS) return
        handler.removeCallbacks(eventRefresh)
        handler.post(eventRefresh)
    }

    private fun refresh() {
        if (!running) return
        val now = moment()
        val config = store.config()
        val before = engine.snapshot()
        if (target != store.targetPackage) {
            engine.update(now, false, config)
            overlay.hide()
            target = store.targetPackage
            watching = false
        }
        val poweredOff = isPoweredOff()
        val interactive = !poweredOff &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val nextWatching = interactive && config.enabled && target.isNotEmpty() && detector.currentPackage() == target
        val state = if (poweredOff) engine.powerOff(now, config) else engine.update(now, nextWatching, config)
        if (!overlay.render(state)) {
            problem = "无法显示提醒窗口 · 请重新启用无障碍服务并检查电视权限"
        } else if (state.reminder != null) {
            problem = null
        }
        val after = engine.snapshot()
        liveSnapshot = after
        val reset = after.usedMs < before.usedMs || after.date != before.date
        if (reset || watching != nextWatching || now.monotonicMs - lastSaved >= 5000) {
            save(now.monotonicMs, durable = poweredOff || reset)
        }
        watching = nextWatching
    }

    private fun save(now: Long, durable: Boolean = false) {
        if (!store.saveSnapshot(engine.snapshot(), durable)) {
            Log.w("TvReminder", "Failed to persist timer snapshot")
        }
        lastSaved = now
    }

    private fun stop() {
        if (!running) return
        if (isPoweredOff()) engine.powerOff(moment(), store.config())
        else engine.update(moment(), false, store.config())
        save(SystemClock.elapsedRealtime(), durable = true)
        running = false
        connected = false
        liveSnapshot = null
        watching = false
        handler.removeCallbacksAndMessages(null)
        overlay.hide()
        store.preferences.unregisterOnSharedPreferenceChangeListener(this)
        unregisterReceiver(screenReceiver)
    }

    override fun onInterrupt() {
        if (running) {
            engine.update(moment(), false, store.config())
            overlay.hide()
            watching = false
            save(SystemClock.elapsedRealtime())
        }
    }
    override fun onUnbind(intent: Intent?): Boolean { stop(); return super.onUnbind(intent) }
    override fun onDestroy() { stop(); super.onDestroy() }

    private fun isPoweredOff() = screenOff || !getSystemService(PowerManager::class.java).isInteractive
    private fun moment() = Moment(SystemClock.elapsedRealtime(), System.currentTimeMillis(), ZoneId.systemDefault(), bootCount)

    companion object {
        @Volatile var connected = false
            private set
        @Volatile var liveSnapshot: Snapshot? = null
            private set
        @Volatile var problem: String? = null
            private set
    }
}
