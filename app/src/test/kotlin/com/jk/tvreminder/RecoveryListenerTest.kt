package com.jk.tvreminder

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.os.Looper
import android.provider.Settings
import java.time.Duration
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class RecoveryListenerTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun enabled() = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

    @Before fun authorize() {
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        shadowOf(app.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(ComponentName(app, RecoveryListener::class.java), true)
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
    }

    @Test fun connectionRestoresAndRetriesAfterFirmwareClearsServiceAgain() {
        val controller = Robolectric.buildService(RecoveryListener::class.java).create()
        controller.get().onListenerConnected()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(enabled().contains("com.jk.tvreminder.ReminderService"))
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(enabled().contains("com.jk.tvreminder.ReminderService"))
        controller.destroy()
    }

    @Test fun destroyingListenerCancelsPendingRecovery() {
        val controller = Robolectric.buildService(RecoveryListener::class.java).create()
        controller.get().onListenerConnected()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(enabled().contains("com.jk.tvreminder.ReminderService"))
        controller.destroy()
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertEquals("", enabled())
    }
}
