package com.jk.tvreminder

import android.content.Context
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.jk.tvreminder.core.Reminder
import com.jk.tvreminder.core.ReminderState

class ReminderOverlay(context: Context, private val onConfirm: () -> Unit) {
    private val themed = ContextThemeWrapper(context, R.style.AppTheme)
    private val manager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var kind: Reminder? = null
    private var countdown: TextView? = null

    /** False means the system rejected the window; the settings screen can explain it. */
    fun render(state: ReminderState): Boolean {
        if (state.reminder == null) { hide(); return true }
        if (kind != state.reminder || root == null) {
            hide()
            val confirm = state.reminder == Reminder.CONFIRM
            val content = object : LinearLayout(themed) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    // Back must not act as an alternative to the explicit confirmation.
                    if (confirm && event.keyCode == KeyEvent.KEYCODE_BACK) return true
                    return super.dispatchKeyEvent(event)
                }
            }.apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = TvStyle.card(themed, TvStyle.background, true)
                val padding = TvStyle.dp(themed, 28)
                setPadding(padding, padding, padding, padding)
                elevation = TvStyle.dp(themed, 16).toFloat()
            }
            content.addView(TvStyle.text(themed, if (confirm) "时间到了哦!" else "时间快到了哦!", 32f).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2))
            var confirmation: View? = null
            if (confirm) {
                content.addView(TvStyle.text(themed, "休息一下，看看远处吧", 18f, true).apply { gravity = Gravity.CENTER }, row(12))
                confirmation = TvStyle.button(themed, "确认") { onConfirm() }.apply {
                    id = R.id.confirm_reminder
                    // Ensure a default selection even if Android last entered touch mode.
                    isFocusableInTouchMode = true
                }
                content.addView(confirmation, row(24))
            } else {
                countdown = TvStyle.text(themed, "", 20f, true).apply { gravity = Gravity.CENTER }
                content.addView(countdown, row(18))
            }
            val params = WindowManager.LayoutParams(
                minOf(TvStyle.dp(themed, 520), themed.resources.displayMetrics.widthPixels - TvStyle.dp(themed, 48)),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    if (confirm) WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.CENTER
                dimAmount = 0.35f
                title = "小电视提醒"
            }
            try {
                manager.addView(content, params)
                root = content
                kind = state.reminder
                confirmation?.requestFocus()
            } catch (_: WindowManager.BadTokenException) { return false }
              catch (_: SecurityException) { return false }
              catch (_: IllegalStateException) { return false }
        }
        countdown?.text = themed.getString(R.string.countdown, state.countdown)
        return true
    }

    fun hide() {
        root?.let { view ->
            try { manager.removeViewImmediate(view) } catch (_: IllegalArgumentException) { /* Already detached by system. */ }
        }
        root = null
        kind = null
        countdown = null
    }

    private fun row(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = TvStyle.dp(themed, top) }
}
