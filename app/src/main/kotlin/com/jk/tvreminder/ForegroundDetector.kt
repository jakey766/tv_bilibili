package com.jk.tvreminder

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityWindowInfo

/** Accessibility overlays do not replace the application being watched. */
class ForegroundDetector(private val service: AccessibilityService) {
    fun currentPackage(): String? = try {
        val windows = service.windows
        // A focused system dialog (for example the home/power UI) suspends counting.
        if (windows.any { it.type == AccessibilityWindowInfo.TYPE_SYSTEM && it.isFocused }) {
            null
        } else {
            val application = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .sortedWith(compareByDescending<AccessibilityWindowInfo> { it.isFocused }.thenByDescending { it.layer })
                .firstOrNull()
            if (application != null) {
                application.root?.let { node ->
                    try { node.packageName?.toString() } finally { @Suppress("DEPRECATION") node.recycle() }
                }
            } else if (windows.isEmpty()) {
                service.rootInActiveWindow?.let { node ->
                    try { node.packageName?.toString() } finally { @Suppress("DEPRECATION") node.recycle() }
                }
            } else null
        }
    } catch (_: SecurityException) { null }
      catch (_: IllegalStateException) { null }
}
