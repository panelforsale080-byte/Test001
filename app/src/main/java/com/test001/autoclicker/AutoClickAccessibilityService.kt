package com.test001.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

class AutoClickAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: AutoClickAccessibilityService? = null

        /** True when our service is in the system's enabled-accessibility list. */
        fun isEnabled(ctx: Context): Boolean {
            val enabled = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.contains(ctx.packageName, ignoreCase = true)
        }
    }

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not used */ }

    override fun onInterrupt() { GesturePlayer.stop() }

    /** Thin wrapper so callers don't touch dispatchGesture directly. */
    fun dispatch(gd: GestureDescription, cb: GestureResultCallback? = null): Boolean =
        try { dispatchGesture(gd, cb, null) } catch (_: Throwable) { false }
}
