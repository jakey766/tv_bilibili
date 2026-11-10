package com.jk.tvreminder

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Optional compatibility path; inert until both extra permissions are explicitly granted. */
object TvRecovery {
    fun authorized(context: Context): Boolean = try {
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED &&
            notificationAccess(context)
    } catch (_: SecurityException) { false }

    private fun notificationAccess(context: Context): Boolean {
        val component = ComponentName(context, RecoveryListener::class.java)
        return if (Build.VERSION.SDK_INT >= 27) {
            // Modern Android keeps listener approval in NotificationManager, not necessarily Settings.Secure.
            context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
        } else {
            components(Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners"))
                .any { ComponentName.unflattenFromString(it) == component }
        }
    }

    fun restore(context: Context): Boolean {
        if (!authorized(context)) return false
        return try {
            val resolver = context.contentResolver
            val original = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val own = ComponentName(context, ReminderService::class.java)
            val services = components(original)
            if (services.none { ComponentName.unflattenFromString(it) == own }) {
                if (!Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        (services + own.flattenToString()).joinToString(":"))) return false
            }
            Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 ||
                Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (_: SecurityException) { false }
    }

    private fun components(value: String?) = value.orEmpty().split(':').filter { it.isNotBlank() && it != "null" }
}
