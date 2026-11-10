package com.jk.tvreminder

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class ForegroundDetectorTest {
    @Test fun confirmationOverlayDoesNotReplaceUnderlyingClient() {
        val controller = Robolectric.buildService(ReminderService::class.java).create()
        val service = controller.get()
        shadowOf(service).setWindows(listOf(
            window("com.jk.tvreminder", AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY, true, 10),
            window("tv.danmaku.bili", AccessibilityWindowInfo.TYPE_APPLICATION, false, 1),
        ))
        assertEquals("tv.danmaku.bili", ForegroundDetector(service).currentPackage())
        controller.destroy()
    }

    @Test fun homeScreenReplacesUnderlyingClient() {
        val controller = Robolectric.buildService(ReminderService::class.java).create()
        val service = controller.get()
        shadowOf(service).setWindows(listOf(
            window("tv.danmaku.bili", AccessibilityWindowInfo.TYPE_APPLICATION, false, 1),
            window("com.android.tv.launcher", AccessibilityWindowInfo.TYPE_APPLICATION, true, 2),
        ))
        assertEquals("com.android.tv.launcher", ForegroundDetector(service).currentPackage())
        controller.destroy()
    }

    @Test fun focusedSystemDialogSuspendsWatching() {
        val controller = Robolectric.buildService(ReminderService::class.java).create()
        val service = controller.get()
        shadowOf(service).setWindows(listOf(
            window("tv.danmaku.bili", AccessibilityWindowInfo.TYPE_APPLICATION, false, 1),
            window("android", AccessibilityWindowInfo.TYPE_SYSTEM, true, 2),
        ))
        assertNull(ForegroundDetector(service).currentPackage())
        controller.destroy()
    }

    companion object {
        @Suppress("DEPRECATION")
        fun window(pkg: String, type: Int, focused: Boolean, layer: Int): AccessibilityWindowInfo {
            val node = AccessibilityNodeInfo.obtain().apply { packageName = pkg }
            return AccessibilityWindowInfo.obtain().also {
                shadowOf(it).apply { setType(type); setFocused(focused); setLayer(layer); setRoot(node) }
            }
        }
    }
}
