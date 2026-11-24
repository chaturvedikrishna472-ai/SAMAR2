package com.samar.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SamarAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Live screen tree rootInActiveWindow se read/interact hota hai
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        if (instance === this) {
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

    fun performScroll(down: Boolean = true): Boolean {
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels * 0.5f
        val startY = if (down) metrics.heightPixels * 0.72f else metrics.heightPixels * 0.28f
        val endY = if (down) metrics.heightPixels * 0.28f else metrics.heightPixels * 0.72f

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 250L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun clickNodeByText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = query.trim()
        if (target.isEmpty()) return false

        val matches = root.findAccessibilityNodeInfosByText(target)
        for (node in matches) {
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable) {
                    return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                current = current.parent
            }
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val targetNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFirstEditableNode(root)
            ?: return false

        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findFirstEditableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    fun performSystemBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun performSystemHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun performSystemRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun readScreenText(maxItems: Int = 25): String {
        val root = rootInActiveWindow ?: return "Active screen ka content nahi mil paaya."
        val collected = LinkedHashSet<String>()
        collectNodeText(root, collected, maxItems)
        return if (collected.isEmpty()) {
            "Screen par koi readable text nahi mila."
        } else {
            collected.joinToString(". ")
        }
    }

    private fun collectNodeText(
        node: AccessibilityNodeInfo?,
        out: LinkedHashSet<String>,
        limit: Int
    ) {
        if (node == null || out.size >= limit) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrEmpty() && text != "SAMAR initializing...") {
            out.add(text)
        } else if (!desc.isNullOrEmpty()) {
            out.add(desc)
        }
        for (i in 0 until node.childCount) {
            if (out.size >= limit) break
            collectNodeText(node.getChild(i), out, limit)
        }
    }

    companion object {
        @Volatile
        var instance: SamarAccessibilityService? = null

        fun isRunning(): Boolean = instance != null
    }
}
