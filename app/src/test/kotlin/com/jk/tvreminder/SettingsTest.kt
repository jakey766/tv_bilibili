package com.jk.tvreminder

import android.widget.EditText
import android.widget.Switch
import android.widget.Button
import android.widget.TextView
import android.provider.Settings
import com.jk.tvreminder.core.ReminderConfig
import com.jk.tvreminder.core.Snapshot
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsTest {
    @Before fun reset() { RuntimeEnvironment.getApplication().getSharedPreferences("reminder", 0).edit().clear().commit() }

    @Test fun defaultsAndSettingsSurviveRecreation() {
        val context = RuntimeEnvironment.getApplication()
        val store = ReminderPreferences(context)
        assertEquals(ReminderConfig(), store.config())
        store.saveConfig(ReminderConfig(false, 20, 25))
        store.targetPackage = "tv.danmaku.bili"
        store.saveSnapshot(Snapshot("2026-11-04", 600000))
        val fresh = ReminderPreferences(context)
        assertEquals(ReminderConfig(false, 20, 25), fresh.config())
        assertEquals("tv.danmaku.bili", fresh.targetPackage)
        assertEquals(Snapshot("2026-11-04", 600000), fresh.snapshot("2026-11-04"))
    }

    @Test fun settingsScreenSavesMinutesAndSwitchImmediately() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<EditText>(R.id.warning_minutes).setText("20")
        activity.findViewById<EditText>(R.id.confirm_minutes).setText("25")
        activity.findViewById<Button>(R.id.save_settings).performClick()
        activity.findViewById<Switch>(R.id.enabled_switch).isChecked = false
        assertEquals(ReminderConfig(false, 20, 25), ReminderPreferences(activity).config())
        controller.pause().stop().destroy()
    }

    @Test fun invalidMinuteOrderDoesNotOverwriteSettings() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<EditText>(R.id.warning_minutes).setText("30")
        activity.findViewById<EditText>(R.id.confirm_minutes).setText("15")
        activity.findViewById<Button>(R.id.save_settings).performClick()
        assertEquals(ReminderConfig(), ReminderPreferences(activity).config())
        assertNotNull(activity.findViewById<EditText>(R.id.confirm_minutes).error)
        controller.pause().stop().destroy()
    }

    @Test fun afterRebootDisplayIsZeroEvenBeforeServiceConnects() {
        val context = RuntimeEnvironment.getApplication()
        ReminderPreferences(context).saveSnapshot(Snapshot(LocalDate.now().toString(), 600000, 10))
        Settings.Global.putInt(context.contentResolver, "boot_count", 11)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("本轮已看  00:00:00", controller.get().findViewById<TextView>(R.id.usage_summary).text.toString())
        controller.pause().stop().destroy()
    }

    @Test fun openingSettingsInSameBootDisplaysSavedUsage() {
        val context = RuntimeEnvironment.getApplication()
        ReminderPreferences(context).saveSnapshot(Snapshot(LocalDate.now().toString(), 600000, 10))
        Settings.Global.putInt(context.contentResolver, "boot_count", 10)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("本轮已看  00:10:00", controller.get().findViewById<TextView>(R.id.usage_summary).text.toString())
        controller.pause().stop().destroy()
    }
}
