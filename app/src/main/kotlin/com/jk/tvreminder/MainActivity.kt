package com.jk.tvreminder

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.jk.tvreminder.core.ReminderConfig
import java.time.LocalDate
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var store: ReminderPreferences
    private lateinit var status: TextView
    private lateinit var recoveryStatus: TextView
    private lateinit var usage: TextView
    private lateinit var targetButton: Button
    private lateinit var warning: EditText
    private lateinit var confirmation: EditText
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { refreshStatus(); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ReminderPreferences(this)
        if (store.targetPackage.isEmpty()) {
            TargetApps.installed(this).firstOrNull { it.recommended }?.let { store.targetPackage = it.packageName }
        }
        val config = store.config()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(44), dp(24), dp(44), dp(32))
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(content) })
        content.addView(TvStyle.text(this, "小电视提醒", 32f))
        content.addView(TvStyle.text(this, "让观看有节奏，让休息有提醒", secondary = true), spaced(6))
        usage = TvStyle.text(this, "本轮已看  00:00", 26f).apply { id = R.id.usage_summary }
        content.addView(usage, spaced(18))
        val toggle = Switch(this).apply {
            id = R.id.enabled_switch
            text = "启用提醒"
            textSize = 22f
            setTextColor(android.graphics.Color.WHITE)
            isChecked = config.enabled
            minHeight = dp(58)
            background = TvStyle.focusBackground(this@MainActivity)
            setPadding(dp(18), dp(6), dp(18), dp(6))
            setOnCheckedChangeListener { _, checked ->
                store.saveConfig(store.config().copy(enabled = checked))
                refreshStatus()
            }
        }
        content.addView(toggle, spaced(14))
        content.addView(TvStyle.text(this, "关闭开关时暂停计时并撤下提醒；本轮进度会保留。", 15f, true), spaced(6))
        warning = minuteRow(content, "开始提醒", R.id.warning_minutes, config.warningMinute)
        confirmation = minuteRow(content, "开始确认提醒", R.id.confirm_minutes, config.confirmMinute)
        content.addView(TvStyle.button(this, "保存提醒时间") { saveMinutes() }.apply { id = R.id.save_settings }, spaced(12))
        content.addView(TvStyle.text(this, "开始提醒后每分钟提示一次，5 秒自动关闭；达到确认时间后，需按【确认】关闭。", 16f, true), spaced(10))
        targetButton = TvStyle.button(this, "选择哔哩哔哩客户端") { chooseTarget() }
        content.addView(targetButton, spaced(18))
        status = TvStyle.text(this, "", 17f, true)
        content.addView(status, spaced(12))
        recoveryStatus = TvStyle.text(this, "", 15f, true)
        content.addView(recoveryStatus, spaced(6))
        content.addView(TvStyle.button(this, "打开无障碍设置") { openAccessibility() }, spaced(10))
        content.addView(TvStyle.text(this, "首次使用：选择客户端 → 在无障碍设置中启用“小电视提醒” → 正常打开哔哩哔哩。\n退出客户端暂停；每次关机或待机后清零，重新开机从 0 开始。跨天也清零。所有数据只保存在本机。", 16f, true), spaced(12))
        toggle.requestFocus()
    }

    private fun minuteRow(parent: LinearLayout, title: String, viewId: Int, initial: Int): EditText {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(TvStyle.text(this, "$title（分钟）", 20f), LinearLayout.LayoutParams(0, dp(58), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        val edit = EditText(this).apply {
            id = viewId
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
            gravity = Gravity.CENTER
            textSize = 22f
            contentDescription = title
            setText(String.format(Locale.US, "%d", initial))
            setSelectAllOnFocus(true)
        }
        row.addView(TvStyle.button(this, "−") {
            val value = edit.text.toString().toIntOrNull() ?: initial
            edit.setText(String.format(Locale.US, "%d", (value.toLong() - 1).coerceAtLeast(1)))
        }.apply { contentDescription = "${title}减少一分钟" }, LinearLayout.LayoutParams(dp(60), dp(52)))
        row.addView(edit, LinearLayout.LayoutParams(dp(100), dp(58)))
        row.addView(TvStyle.button(this, "+") {
            val value = edit.text.toString().toIntOrNull() ?: initial
            edit.setText(String.format(Locale.US, "%d", (value.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong())))
        }.apply { contentDescription = "${title}增加一分钟" }, LinearLayout.LayoutParams(dp(60), dp(52)))
        parent.addView(row, spaced(12))
        return edit
    }

    private fun saveMinutes() {
        warning.error = null
        confirmation.error = null
        val start = warning.text.toString().toIntOrNull()
        val confirm = confirmation.text.toString().toIntOrNull()
        if (start == null || start <= 0) {
            warning.error = "请输入大于 0 的整数分钟"
            warning.requestFocus()
            return
        }
        if (confirm == null || confirm <= start) {
            confirmation.error = "确认提醒时间必须晚于开始提醒时间"
            confirmation.requestFocus()
            return
        }
        store.saveConfig(ReminderConfig(store.config().enabled, start, confirm))
        Toast.makeText(this, "提醒时间已保存", Toast.LENGTH_SHORT).show()
    }

    private fun chooseTarget() {
        val apps = TargetApps.installed(this)
        if (apps.isEmpty()) {
            Toast.makeText(this, "未找到可启动的应用，请先安装哔哩哔哩客户端", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this).setTitle("选择要计时的哔哩哔哩客户端")
            .setSingleChoiceItems(apps.map { "${it.label}\n${it.packageName}" }.toTypedArray(), apps.indexOfFirst { it.packageName == store.targetPackage }) { dialog, which ->
                store.targetPackage = apps[which].packageName
                refreshStatus()
                dialog.dismiss()
            }.setNegativeButton("取消", null).show()
    }

    private fun openAccessibility() {
        try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        catch (_: ActivityNotFoundException) {
            AlertDialog.Builder(this).setTitle("请从电视设置开启")
                .setMessage("本机未提供标准无障碍入口。请在电视设置中查找“无障碍”或“辅助功能”，启用“小电视提醒”。")
                .setPositiveButton("知道了", null).show()
        }
    }

    private fun refreshStatus() {
        val today = LocalDate.now().toString()
        val snapshot = ReminderService.liveSnapshot ?: store.snapshot(today)
        val bootCount = try { Settings.Global.getInt(contentResolver, "boot_count", -1) }
            catch (_: SecurityException) { -1 }
        val rebooted = bootCount >= 0 && snapshot.bootCount >= 0 && bootCount != snapshot.bootCount
        val seconds = if (snapshot.date == today && !rebooted) snapshot.usedMs / 1000 else 0
        usage.text = String.format(Locale.CHINA, "本轮已看  %02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        val target = store.targetPackage
        val label = if (target.isEmpty()) "未选择" else try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString()
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) { "客户端已卸载，请重新选择" }
        targetButton.text = getString(R.string.target_client, label)
        status.text = when {
            !store.config().enabled -> "提醒已关闭 · 本轮进度已保留"
            target.isEmpty() -> "尚未选择客户端"
            !ReminderService.connected -> "尚未连接无障碍服务 · 请在下方设置中启用"
            ReminderService.problem != null -> ReminderService.problem
            else -> "提醒已就绪 · 打开所选客户端后自动累计"
        }
        recoveryStatus.text = if (TvRecovery.authorized(this)) "开机自动恢复：已授权"
            else "开机自动恢复：未授权 · 部分电视待机后会停止提醒服务"
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    private fun dp(value: Int) = TvStyle.dp(this, value)
    private fun spaced(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
}
