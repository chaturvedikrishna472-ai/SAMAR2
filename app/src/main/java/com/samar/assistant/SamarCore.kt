package com.samar.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

object SamarCore {

    val requiredRuntimePermissions: List<String> = listOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.SEND_SMS,
        Manifest.permission.CAMERA
    )

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun isAccessibilityEnabled(): Boolean {
        return SamarAccessibilityService.isRunning()
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun tapScreen(x: Float, y: Float): String {
        val service = SamarAccessibilityService.instance
            ?: return "SAMAR ki Accessibility Service off hai. Pehle settings mein on karo."
        val ok = service.performTap(x, y)
        return if (ok) {
            "Tap dispatched at ($x, $y)."
        } else {
            "Tap dispatch fail ho gaya."
        }
    }

    fun readScreen(maxItems: Int = 25): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo tabhi screen padh paunga."
        return service.readScreenText(maxItems)
    }

    fun sendSms(context: Context, phoneNumber: String, message: String): String {
        if (!hasPermission(context, Manifest.permission.SEND_SMS)) {
            return "SEND_SMS permission nahi mili."
        }
        return try {
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            "SMS bhej diya gaya $phoneNumber par."
        } catch (e: Exception) {
            "SMS bhejne mein error: ${e.message ?: "unknown error"}"
        }
    }

    fun openAppByPackage(context: Context, packageName: String): String {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            "Opening $packageName."
        } else {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
            "$packageName install nahi mila, store khol diya hai."
        }
    }

    fun handleCommand(context: Context, rawCommand: String): String {
        val command = rawCommand.trim().lowercase()
        if (command.isEmpty()) {
            return "Kuch sunai nahi diya bhai, phir se bolo."
        }

        return when {
            command.contains("read screen") || command.contains("screen padh") -> {
                readScreen()
            }
            command.contains("accessibility") -> {
                openAccessibilitySettings(context)
                "Accessibility settings khol di hain, ab SAMAR ko power do."
            }
            command.startsWith("open ") -> {
                val target = command.removePrefix("open ").trim()
                when (target) {
                    "settings" -> {
                        context.startActivity(
                            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        "Phone settings khol di hain."
                    }
                    "camera" -> {
                        openAppByPackage(context, "com.android.camera")
                    }
                    else -> {
                        "Command samajh aa gayi: '$rawCommand'. Exact package ya action aage jodenge."
                    }
                }
            }
            else -> {
                "SAMAR online aur sarcastic mode ready — command mila: '$rawCommand'"
            }
        }
    }
}
