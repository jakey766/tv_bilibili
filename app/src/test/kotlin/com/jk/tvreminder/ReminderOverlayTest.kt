package com.jk.tvreminder

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.Switch
import com.jk.tvreminder.core.Reminder
import com.jk.tvreminder.core.ReminderState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class ReminderOverlayTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun views() = Shadow.extract<ShadowWindowManagerImpl>(context.getSystemService(WindowManager::class.java)).views

    @Test fun confirmationHasFocusConsumesBackAndRequiresConfirm() {
        var confirmed = 0
        val overlay = ReminderOverlay(context) { confirmed++ }
        assertTrue(overlay.render(ReminderState(900000, Reminder.CONFIRM, 0)))
        // WindowManager attaches synchronously but the first layout runs on the main loop.
        shadowOf(Looper.getMainLooper()).idle()
        val root = views().single()
        assertTrue(root.width > 0)
        val button = root.findViewById<Button>(R.id.confirm_reminder)
        assertNotNull(button)
        assertTrue(button.hasFocus())
        assertNull(root.findViewById<Switch>(R.id.enabled_switch))
        assertTrue(root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        assertTrue(root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
        assertEquals(0, confirmed)
        root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, confirmed)
        overlay.hide()
        assertTrue(views().isEmpty())
    }

    @Test fun repeatedRenderDoesNotStackWindows() {
        val overlay = ReminderOverlay(context) {}
        repeat(20) { assertTrue(overlay.render(ReminderState(900000, Reminder.CONFIRM, 0))) }
        assertEquals(1, views().size)
        overlay.hide()
    }

    @Test fun warningDoesNotTakeRemoteFocusAndCanBeRemoved() {
        val overlay = ReminderOverlay(context) {}
        assertTrue(overlay.render(ReminderState(780000, Reminder.WARNING, 5)))
        val root = views().single()
        val params = root.layoutParams as WindowManager.LayoutParams
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertNull(root.findViewById<Button>(R.id.confirm_reminder))
        overlay.render(ReminderState(785000, null, 0))
        assertTrue(views().isEmpty())
    }
}
