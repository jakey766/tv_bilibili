package com.jk.tvreminder

import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import com.jk.tvreminder.core.ReminderConfig
import com.jk.tvreminder.core.Snapshot
import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class ReminderServiceTest {
    @Before fun reset() {
        RuntimeEnvironment.getApplication().getSharedPreferences("reminder", 0).edit().clear().commit()
        Settings.Global.putInt(RuntimeEnvironment.getApplication().contentResolver, "boot_count", 10)
    }

    @Test fun overlayAndHomeSwitchPreserveUsageAndStopBackgroundCounting() {
        val controller = startService()
        val service = controller.get()
        assertEquals(1, views(service).size)
        val initial = ReminderService.liveSnapshot!!.usedMs
        shadowOf(service).setWindows(listOf(ForegroundDetectorTest.window("launcher", AccessibilityWindowInfo.TYPE_APPLICATION, true, 1)))
        idle(1000)
        assertTrue(views(service).isEmpty())
        val paused = ReminderService.liveSnapshot!!.usedMs
        assertTrue(paused >= initial)
        idle(5000)
        assertEquals(paused, ReminderService.liveSnapshot!!.usedMs)
        setClient(service)
        idle(500)
        assertEquals(1, views(service).size)
        controller.destroy()
        assertFalse(ReminderService.connected)
        assertTrue(views(service).isEmpty())
    }

    @Test fun screenOffAfterConfirmationStartsANewSession() {
        val controller = startService()
        val service = controller.get()
        shadowOf(service.getSystemService(PowerManager::class.java)).turnScreenOn(false)
        idle(500)
        assertTrue(views(service).isEmpty())
        val paused = ReminderService.liveSnapshot!!.usedMs
        assertEquals(0L, paused)
        idle(1000)
        assertEquals(paused, ReminderService.liveSnapshot!!.usedMs)
        shadowOf(service.getSystemService(PowerManager::class.java)).turnScreenOn(true)
        idle(500)
        assertTrue(views(service).isEmpty())
        assertTrue(ReminderService.liveSnapshot!!.usedMs < 1000)
        val prefs = ReminderPreferences(service)
        prefs.saveConfig(prefs.config().copy(enabled = false))
        idle(500)
        assertTrue(views(service).isEmpty())
        val disabled = ReminderService.liveSnapshot!!.usedMs
        idle(1000)
        assertEquals(disabled, ReminderService.liveSnapshot!!.usedMs)
        controller.destroy()
    }

    @Test fun successfulReminderClearsPreviousWindowError() {
        val controller = startService()
        val service = controller.get()
        ReflectionHelpers.setStaticField(ReminderService::class.java, "problem", "此前窗口显示失败")
        idle(500)
        assertEquals(1, views(service).size)
        assertNull(ReminderService.problem)
        controller.destroy()
    }

    @Test fun screenOffBeforeReminderAlsoResetsAndPersistsImmediately() {
        val controller = startService(initialUsage = 30000)
        val service = controller.get()
        assertTrue(ReminderService.liveSnapshot!!.usedMs >= 30000)
        shadowOf(service.getSystemService(PowerManager::class.java)).turnScreenOn(false)
        idle(1)
        assertEquals(0L, ReminderService.liveSnapshot!!.usedMs)
        assertEquals(0L, ReminderPreferences(service).snapshot(LocalDate.now().toString()).usedMs)
        controller.destroy()
    }

    @Test fun screenOffResetsEvenAfterLeavingClientAndDisablingReminders() {
        val controller = startService(initialUsage = 30000)
        val service = controller.get()
        shadowOf(service).setWindows(listOf(ForegroundDetectorTest.window("launcher", AccessibilityWindowInfo.TYPE_APPLICATION, true, 1)))
        val prefs = ReminderPreferences(service)
        prefs.saveConfig(prefs.config().copy(enabled = false))
        idle(500)
        assertTrue(ReminderService.liveSnapshot!!.usedMs >= 30000)
        shadowOf(service.getSystemService(PowerManager::class.java)).turnScreenOn(false)
        idle(500)
        assertEquals(0L, ReminderService.liveSnapshot!!.usedMs)
        controller.destroy()
    }

    @Test fun normalShutdownResetsBeforeServiceIsDestroyed() {
        val controller = startService(initialUsage = 30000)
        val service = controller.get()
        service.sendBroadcast(Intent(Intent.ACTION_SHUTDOWN))
        idle(500)
        assertEquals(0L, ReminderService.liveSnapshot!!.usedMs)
        assertTrue(views(service).isEmpty())
        controller.destroy()
    }

    @Test fun restartingServiceInSameBootKeepsUsage() {
        val first = startService(initialUsage = 30000)
        first.destroy()
        val next = startService(resetPreferences = false)
        assertTrue(ReminderService.liveSnapshot!!.usedMs >= 30000)
        assertEquals(10, ReminderService.liveSnapshot!!.bootCount)
        next.destroy()
    }

    @Test fun fullRebootResetsWithoutReceivingShutdownBroadcast() {
        val first = startService(initialUsage = 30000)
        first.destroy()
        Settings.Global.putInt(RuntimeEnvironment.getApplication().contentResolver, "boot_count", 11)
        val next = startService(resetPreferences = false)
        assertTrue(ReminderService.liveSnapshot!!.usedMs < 1000)
        assertEquals(11, ReminderPreferences(next.get()).snapshot(LocalDate.now().toString()).bootCount)
        next.destroy()
    }

    private fun startService(initialUsage: Long = 120000, resetPreferences: Boolean = true): org.robolectric.android.controller.ServiceController<ReminderService> {
        val controller = Robolectric.buildService(ReminderService::class.java).create()
        val service = controller.get()
        val prefs = ReminderPreferences(service)
        if (resetPreferences) {
            prefs.targetPackage = "tv.danmaku.bili"
            prefs.saveConfig(ReminderConfig(true, 1, 2))
            prefs.saveSnapshot(Snapshot(LocalDate.now().toString(), initialUsage))
        }
        shadowOf(service.getSystemService(PowerManager::class.java)).turnScreenOn(true)
        setClient(service)
        ReflectionHelpers.callInstanceMethod<Void>(service, "onServiceConnected")
        idle(500)
        return controller
    }

    private fun setClient(service: ReminderService) {
        shadowOf(service).setWindows(listOf(ForegroundDetectorTest.window("tv.danmaku.bili", AccessibilityWindowInfo.TYPE_APPLICATION, true, 1)))
    }
    private fun idle(ms: Long) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)) }
    private fun views(service: ReminderService) = Shadow.extract<ShadowWindowManagerImpl>(service.getSystemService(WindowManager::class.java)).views
}
