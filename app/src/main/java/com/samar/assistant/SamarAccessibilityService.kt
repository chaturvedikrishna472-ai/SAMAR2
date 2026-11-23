package com.samar.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class SamarAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Aage ke steps mein yahan active app / screen events handle honge
    }

    override fun onInterrupt() {
        // Service interrupt hone par safe fallback
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }

    fun performTap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile
        var instance: SamarAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
