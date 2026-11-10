package com.jk.tvreminder

import android.content.Context
import android.content.Intent

data class TargetApp(val packageName: String, val label: String) {
    val recommended: Boolean get() = packageName.contains("bili", true) ||
        packageName == "com.xiaodianshi.tv.yst" || label.contains("哔哩") || label.contains("小电视")
}

object TargetApps {
    @Suppress("DEPRECATION")
    fun installed(context: Context): List<TargetApp> {
        val manager = context.packageManager
        return listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).flatMap { category ->
            manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
        }.map { TargetApp(it.activityInfo.packageName, it.loadLabel(manager).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedWith(compareByDescending<TargetApp> { it.recommended }.thenBy { it.label })
    }
}
