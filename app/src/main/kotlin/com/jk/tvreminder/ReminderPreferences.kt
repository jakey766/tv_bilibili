package com.jk.tvreminder

import android.content.Context
import com.jk.tvreminder.core.ReminderConfig
import com.jk.tvreminder.core.Snapshot

class ReminderPreferences(context: Context) {
    val preferences = context.getSharedPreferences("reminder", Context.MODE_PRIVATE)

    fun config(): ReminderConfig = ReminderConfig(
        preferences.getBoolean("enabled", true),
        preferences.getInt("warning", 13),
        preferences.getInt("confirm", 15),
    )

    fun saveConfig(config: ReminderConfig) {
        preferences.edit().putBoolean("enabled", config.enabled)
            .putInt("warning", config.warningMinute).putInt("confirm", config.confirmMinute).apply()
    }

    var targetPackage: String
        get() = preferences.getString("target", "").orEmpty()
        set(value) { preferences.edit().putString("target", value).apply() }

    fun snapshot(today: String): Snapshot = Snapshot(
        preferences.getString("date", today) ?: today,
        preferences.getLong("used", 0).coerceAtLeast(0),
        preferences.getInt("boot_count", -1),
    )

    fun saveSnapshot(snapshot: Snapshot, durable: Boolean = false): Boolean {
        val editor = preferences.edit().putString("date", snapshot.date).putLong("used", snapshot.usedMs)
            .putInt("boot_count", snapshot.bootCount)
        // Power-off resets must reach disk before the process is suspended or stopped.
        if (durable) return editor.commit()
        editor.apply()
        return true
    }

    companion object {
        val SETTING_KEYS = setOf("enabled", "warning", "confirm", "target")
    }
}
