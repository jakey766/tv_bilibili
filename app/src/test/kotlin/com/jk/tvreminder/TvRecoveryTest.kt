package com.jk.tvreminder

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class TvRecoveryTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val resolver get() = app.contentResolver
    private val listener = "com.jk.tvreminder/.RecoveryListener"
    private val reminder = "com.jk.tvreminder/com.jk.tvreminder.ReminderService"
    private val other = "example.other/.Service"

    @Before fun reset() {
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        notificationAccess(false)
        Settings.Secure.putString(resolver, "enabled_notification_listeners", "")
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, other)
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
    }

    @Test fun withoutPermissionDoesNotChangeAnySetting() {
        assertFalse(TvRecovery.restore(app))
        assertEquals(other, enabled())
    }

    @Test fun secureSettingsPermissionAloneIsInsufficient() {
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        assertFalse(TvRecovery.restore(app))
        assertEquals(other, enabled())
    }

    @Test fun notificationAccessAloneIsInsufficient() {
        notificationAccess(true)
        assertFalse(TvRecovery.restore(app))
        assertEquals(other, enabled())
    }

    @Test fun restoresOnlyOwnServiceAndPreservesOtherServices() {
        authorize()
        assertTrue(TvRecovery.restore(app))
        assertEquals("$other:$reminder", enabled())
        assertTrue(TvRecovery.restore(app))
        assertEquals("$other:$reminder", enabled())
    }

    @Test fun abbreviatedExistingComponentIsNotDuplicated() {
        authorize()
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "$other:com.jk.tvreminder/.ReminderService")
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        assertTrue(TvRecovery.restore(app))
        assertEquals("$other:com.jk.tvreminder/.ReminderService", enabled())
        assertEquals(1, Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED))
    }

    @Test fun revokingNotificationAccessStopsRecovery() {
        authorize()
        assertTrue(TvRecovery.authorized(app))
        notificationAccess(false)
        assertFalse(TvRecovery.restore(app))
        assertEquals(other, enabled())
    }

    private fun authorize() {
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        notificationAccess(true)
    }

    @Test fun modernGrantWorksWithoutLegacySecureSetting() {
        authorize()
        assertEquals("", Settings.Secure.getString(resolver, "enabled_notification_listeners"))
        assertTrue(TvRecovery.authorized(app))
    }

    @Test fun staleLegacySettingDoesNotOverrideModernRevocation() {
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.Secure.putString(resolver, "enabled_notification_listeners", listener)
        notificationAccess(false)
        assertFalse(TvRecovery.authorized(app))
        assertFalse(TvRecovery.restore(app))
        assertEquals(other, enabled())
    }

    private fun notificationAccess(allowed: Boolean) {
        shadowOf(app.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(ComponentName(app, RecoveryListener::class.java), allowed)
    }
    private fun enabled() = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
}
