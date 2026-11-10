package com.jk.tvreminder

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService

/** Uses only system lifecycle callbacks; never accesses notification data. */
class RecoveryListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private val recover = Runnable {
        if (!ReminderService.connected) TvRecovery.restore(this)
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { scheduleRecovery() }
    }

    override fun onListenerConnected() {
        if (!registered) {
            val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
            else { @Suppress("DEPRECATION") registerReceiver(screenReceiver, filter) }
            registered = true
        }
        scheduleRecovery()
    }

    private fun scheduleRecovery() {
        handler.removeCallbacks(recover)
        handler.post(recover)
        handler.postDelayed(recover, 2000)
        handler.postDelayed(recover, 8000)
    }

    override fun onListenerDisconnected() {
        cleanup()
        if (Build.VERSION.SDK_INT >= 24 && TvRecovery.authorized(this)) {
            try { requestRebind(ComponentName(this, RecoveryListener::class.java)) }
            catch (_: SecurityException) { /* Access may have been revoked concurrently. */ }
        }
    }

    override fun onDestroy() { cleanup(); super.onDestroy() }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        if (registered) {
            unregisterReceiver(screenReceiver)
            registered = false
        }
    }
}
