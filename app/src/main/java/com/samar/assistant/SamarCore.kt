package com.samar.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

object SamarCore {

    private val mainHandler = Handler(Looper.getMainLooper())
    const val CLEAR_LOG_SIGNAL = "__SAMAR_CLEAR_LOG__"
    private var lastRepeatableCommand: String? = null

    private val commonAppPackages: Map<String, String> = mapOf(
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "play store" to "com.android.vending",
        "phone" to "com.android.dialer",
        "dialer" to "com.android.dialer",
        "messages" to "com.google.android.apps.messaging",
        "camera" to "com.android.camera",
        "instagram" to "com.instagram.android",
        "maps" to "com.google.android.apps.maps",
        "clock" to "com.android.deskclock",
        "contacts" to "com.android.contacts",
        "gallery" to "com.miui.gallery",
        "photos" to "com.google.android.apps.photos"
    )

    val requiredRuntimePermissions: List<String> = listOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.SEND_SMS,
        Manifest.permission.CAMERA
    )

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun isAccessibilityEnabled(): Boolean = SamarAccessibilityService.isRunning()

    fun getStatusSummary(context: Context): String {
        val acc = if (isAccessibilityEnabled()) "ON" else "OFF"
        val mic = if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) "OK" else "MISSING"
        val sms = if (hasPermission(context, Manifest.permission.SEND_SMS)) "OK" else "MISSING"
        return "SAMAR status: Accessibility $acc, Mic $mic, SMS $sms."
    }

    fun getCommandsHelp(): String =
        "Commands: open <app/settings/wifi/bluetooth/sound/battery/display/location/date>, call <number>, click <text>, find <text>, tap <x> <y>, type <text>, search <text>, sms <number> : <msg>, read screen, scroll down/up, back, home, recents, repeat, clear log, status."

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openDialer(context: Context, phoneNumber: String): String {
        val clean = phoneNumber.trim()
        if (clean.isEmpty()) return "Kisko dial karna hai uska number batao."
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return "$clean ke liye dialer khol diya hai."
    }

    fun tapScreen(x: Float, y: Float): String {
        val service = SamarAccessibilityService.instance ?: return "Accessibility service off hai boss."
        return if (service.performTap(x, y)) "($x, $y) par tap kar diya." else "Tap fail ho gaya."
    }

    fun clickText(targetText: String): String {
        val service = SamarAccessibilityService.instance ?: return "Accessibility service off hai bhai."
        val clean = targetText.trim()
        if (clean.isEmpty()) return "Kya click karna hai uska naam batao."
        return if (service.clickNodeByText(clean)) "'$clean' par click kar diya." else "'$clean' nahi mila."
    }

    fun findOrClickText(targetText: String): String {
        val service = SamarAccessibilityService.instance ?: return "Accessibility service off hai bhai."
        val clean = targetText.trim()
        if (clean.isEmpty()) return "Kya dhoondhna hai wo batao."
        if (service.clickNodeByText(clean)) return "'$clean' mil gaya aur click kar diya."
        if (service.readScreenText(40).contains(clean, ignoreCase = true)) {
            return "'$clean' screen par dikh raha hai, par direct clickable nahi mila."
        }
        if (service.performScroll(true)) {
            mainHandler.postDelayed({ service.clickNodeByText(clean) }, 600L)
            return "'$clean' neeche scroll karke dhoondh raha hoon."
        }
        return "'$clean' is screen par nahi mila."
    }

    fun typeIntoField(text: String): String {
        val service = SamarAccessibilityService.instance ?: return "Accessibility service off hai bhai."
        val clean = text.trim()
        if (clean.isEmpty()) return "Kya type karna hai wo batao."
        return if (service.typeText(clean)) "'$clean' type kar diya." else "Editable box nahi mila."
    }

    fun searchOnScreen(query: String): String {
        val service = SamarAccessibilityService.instance ?: return "Accessibility service off hai bhai."
        val clean = query.trim()
        if (clean.isEmpty()) return "Kya search karna hai wo bolo."
        service.clickNodeByText("Search")
        return if (service.typeText(clean)) "'$clean' search mein daal diya." else "Search box nahi mila."
    }

    fun goBack(): String = SamarAccessibilityService.instance?.let {
        if (it.performSystemBack()) "Ek kadam peeche." else "Back fail ho gaya."
    } ?: "Accessibility service off hai bhai."

    fun goHome(): String = SamarAccessibilityService.instance?.let {
        if (it.performSystemHome()) "Home screen par wapas." else "Home fail ho gaya."
    } ?: "Accessibility service off hai bhai."

    fun openRecents(): String = SamarAccessibilityService.instance?.let {
        if (it.performSystemRecents()) "Recent apps khol di hain." else "Recents fail ho gaya."
    } ?: "Accessibility service off hai bhai."

    fun scrollScreen(down: Boolean = true): String {
        val service = SamarAccessibilityService.instance ?: return "Accessibility service off hai bhai."
        val dir = if (down) "neeche" else "upar"
        return if (service.performScroll(down)) "Screen $dir scroll kar di." else "Scroll fail ho gaya."
    }

    fun readScreen(maxItems: Int = 25): String =
        SamarAccessibilityService.instance?.readScreenText(maxItems) ?: "Accessibility service off hai bhai."

    fun sendSms(context: Context, phoneNumber: String, message: String): String {
        if (!hasPermission(context, Manifest.permission.SEND_SMS)) return "SEND_SMS permission nahi mili."
        return try {
            @Suppress("DEPRECATION")
            SmsManager.getDefault().sendTextMessage(phoneNumber, null, message, null, null)
            "SMS bhej diya gaya $phoneNumber par."
        } catch (e: Exception) {
            "SMS error: ${e.message ?: "unknown error"}"
        }
    }

    fun openAppByPackage(context: Context, packageName: String): String {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            "$packageName khol diya hai."
        } else {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "$packageName nahi mila, Play Store khol diya."
        }
    }

    fun handleCommand(
        context: Context,
        rawCommand: String,
        onAsyncStepResult: ((String) -> Unit)? = null
    ): String {
        val appContext = context.applicationContext
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return "Kuch sunai nahi diya bhai."

        val steps = trimmed.split(Regex("(?i)\\s+(?:and|then)\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (steps.size > 1) {
            val results = mutableListOf<String>()
            steps.forEachIndexed { index, step ->
                if (index == 0) {
                    results.add(runStep(appContext, step, true))
                } else {
                    mainHandler.postDelayed({
                        onAsyncStepResult?.invoke(runStep(appContext, step, false))
                    }, index * 1100L)
                    results.add("Queued: '$step'")
                }
            }
            return results.joinToString(" | ")
        }
        return runStep(appContext, trimmed, true)
    }

    private fun runStep(context: Context, rawCommand: String, saveRepeat: Boolean): String {
        val command = rawCommand.trim().lowercase()
        if (command.isEmpty()) return "Kuch sunai nahi diya bhai."

        if (command == "repeat" || command == "phir se" || command == "dobara") {
            val prev = lastRepeatableCommand ?: return "Koi pichla action command record nahi hai."
            return runStep(context, prev, false)
        }

        val repeatable = command.startsWith("open ") || command.startsWith("call ") ||
            command.startsWith("dial ") || command.startsWith("tap ") || command.startsWith("click ") ||
            command.startsWith("find ") || command.startsWith("type ") || command.startsWith("write ") ||
            command.startsWith("search ") || command.contains("scroll down") ||
            command.contains("scroll up") || command.contains("back")
        if (saveRepeat && repeatable) lastRepeatableCommand = rawCommand.trim()

        return when {
            command == "clear log" || command == "clear chat" || command == "reset log" || command == "clear" ->
                CLEAR_LOG_SIGNAL
            command == "hello" || command == "hi" || command == "hey" || command.startsWith("namaste") ->
                "Haan boss, SAMAR hazir hai. Bol kya karna hai?"
            command.contains("who are you") || command.contains("kaun hai tu") || command.contains("intro") ->
                "Main SAMAR hoon—JARVIS ki precision aur Deadpool ka attitude."
            command == "help" || command == "commands" -> getCommandsHelp()
            command == "status" || command.contains("system check") -> getStatusSummary(context)
            command.startsWith("call ") || command.startsWith("dial ") ->
                openDialer(context, rawCommand.trim().substringAfter(" ", "").trim())
            command.startsWith("tap ") -> {
                val coords = command.removePrefix("tap ").trim().split(Regex("[,\\s]+"))
                val x = coords.getOrNull(0)?.toFloatOrNull()
                val y = coords.getOrNull(1)?.toFloatOrNull()
                if (x != null && y != null) tapScreen(x, y) else "Format: tap <x> <y>"
            }
            command.startsWith("sms ") || command.startsWith("send sms ") -> {
                val payload = rawCommand.trim().replaceFirst(Regex("(?i)^send\\s+sms\\s+|^sms\\s+"), "").trim()
                val sep = payload.indexOf(':')
                if (sep > 0) {
                    val num = payload.substring(0, sep).trim()
                    val msg = payload.substring(sep + 1).trim()
                    if (num.isNotEmpty() && msg.isNotEmpty()) sendSms(context, num, msg)
                    else "Format: sms <number> : <message>"
                } else {
                    val parts = payload.split(Regex("\\s+"), limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                        sendSms(context, parts[0].trim(), parts[1].trim())
                    } else "Format: sms <number> : <message>"
                }
            }
            command.startsWith("search ") -> searchOnScreen(rawCommand.trim().substringAfter(" ", "").trim())
            command.startsWith("type ") || command.startsWith("write ") ->
                typeIntoField(rawCommand.trim().substringAfter(" ", "").trim())
            command.startsWith("find ") -> findOrClickText(rawCommand.trim().substringAfter(" ", "").trim())
            command.startsWith("click ") -> clickText(rawCommand.trim().substringAfter(" ", "").trim())
            command.contains("go back") || command == "back" || command.contains("peeche ja") -> goBack()
            command.contains("go home") || command == "home" || command.contains("home screen") -> goHome()
            command.contains("recent") || command.contains("recents") -> openRecents()
            command.contains("scroll down") || command.contains("neeche scroll") -> scrollScreen(down = true)
            command.contains("scroll up") || command.contains("upar scroll") -> scrollScreen(down = false)
            command.contains("read screen") || command.contains("screen padh") -> readScreen()
            command.contains("accessibility") -> {
                openAccessibilitySettings(context)
                "Accessibility settings khol di hain."
            }
            command.startsWith("open ") -> {
                val target = command.removePrefix("open ").trim()
                val action = when (target) {
                    "settings" -> Settings.ACTION_SETTINGS
                    "wifi" -> Settings.ACTION_WIFI_SETTINGS
                    "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                    "sound" -> Settings.ACTION_SOUND_SETTINGS
                    "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                    "display" -> Settings.ACTION_DISPLAY_SETTINGS
                    "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                    "date" -> Settings.ACTION_DATE_SETTINGS
                    else -> null
                }
                when {
                    action != null -> {
                        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        "$target settings khol di hain."
                    }
                    commonAppPackages.containsKey(target) ->
                        openAppByPackage(context, commonAppPackages.getValue(target))
                    target.contains(".") -> openAppByPackage(context, target)
                    else -> "'$target' ka package nahi mila."
                }
            }
            else -> "SAMAR sun raha hai—'$rawCommand' bola tune."
        }
    }
}
