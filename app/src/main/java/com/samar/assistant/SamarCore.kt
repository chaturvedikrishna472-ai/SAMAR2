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

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun isAccessibilityEnabled(): Boolean {
        return SamarAccessibilityService.isRunning()
    }

    fun getStatusSummary(context: Context): String {
        val acc = if (isAccessibilityEnabled()) "ON" else "OFF"
        val mic = if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) "OK" else "MISSING"
        val sms = if (hasPermission(context, Manifest.permission.SEND_SMS)) "OK" else "MISSING"
        return "SAMAR status: Accessibility $acc, Mic $mic, SMS$sms. Main taiyar hoon, ab tu apna plan mat bigaadna."
    }

    fun getCommandsHelp(): String {
        return "Commands: open <app/settings/wifi/bluetooth/sound/battery/display/location/date>, call <number>, click <text>, find <text>, tap <x> <y>, type <text>, search <text>, sms <number> : <msg>, read screen, scroll down/up, back, home, recents, repeat, clear log, status."
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openDialer(context: Context, phoneNumber: String): String {
        val cleanNumber = phoneNumber.trim()
        if (cleanNumber.isEmpty()) {
            return "Kisko call/dial karna hai uska number toh batao."
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "$cleanNumber ke liye dialer khol diya hai."
    }

    fun tapScreen(x: Float, y: Float): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai boss. Pehle settings mein on karo."
        val ok = service.performTap(x, y)
        return if (ok) "($x,$y) par tap kar diya hai." else "Tap dispatch fail ho gaya."
    }

    fun clickText(targetText: String): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        val cleanTarget = targetText.trim()
        if (cleanTarget.isEmpty()) return "Kya click karna hai uska naam toh batao."
        val ok = service.clickNodeByText(cleanTarget)
        return if (ok) "'$cleanTarget' par click kar diya hai." else "Screen par '$cleanTarget' nahi mila."
    }

    fun findOrClickText(targetText: String): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        val cleanTarget = targetText.trim()
        if (cleanTarget.isEmpty()) return "Screen par kya dhoondhna hai wo toh batao."
        if (service.clickNodeByText(cleanTarget)) {
            return "'$cleanTarget' mil gaya aur click kar diya hai."
        }
        val screenContent = service.readScreenText(40)
        if (screenContent.contains(cleanTarget, ignoreCase = true)) {
            return "'$cleanTarget' screen par dikh raha hai, par direct clickable node nahi mila."
        }
        if (service.performScroll(true)) {
            mainHandler.postDelayed({ service.clickNodeByText(cleanTarget) }, 600L)
            return "'$cleanTarget' turant nahi mila, neeche scroll karke dhoondh raha hoon."
        }
        return "'$cleanTarget' abhi is screen par nahi mila."
    }

    fun typeIntoField(text: String): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return "Kya type karna hai wo toh batao."
        return if (service.typeText(cleanText)) {
            "'$cleanText' likh diya hai input box mein."
        } else {
            "Screen par koi editable text box nahi mila."
        }
    }

    fun searchOnScreen(query: String): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return "Kya search karna hai wo toh bolo."
        service.clickNodeByText("Search")
        return if (service.typeText(cleanQuery)) {
            "'$cleanQuery' search field mein daal diya hai."
        } else {
            "Search box nahi mil paaya."
        }
    }

    fun goBack(): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        return if (service.performSystemBack()) "Ek kadam peeche." else "Back action nahi ho paaya."
    }

    fun goHome(): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        return if (service.performSystemHome()) "Home screen par wapas." else "Home action nahi ho paaya."
    }

    fun openRecents(): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        return if (service.performSystemRecents()) "Recent apps khol di hain." else "Recent apps nahi khul paayin."
    }

    fun scrollScreen(down: Boolean = true): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        val ok = service.performScroll(down)
        val direction = if (down) "neeche" else "upar"
        return if (ok) "Screen $direction scroll kar di hai." else "Screen scroll nahi ho paayi."
    }

    fun readScreen(maxItems: Int = 25): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
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
            "$packageName khol diya hai, sir."
        } else {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
            "$packageName install nahi mila, Play Store khol diya hai."
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
                    results.add(executeCommand(appContext, step, true))
                } else {
                    val delayMs = index * 1100L
                    mainHandler.postDelayed({
                        val stepReply = executeCommand(appContext, step, false)
                        onAsyncStepResult?.invoke(stepReply)
                    }, delayMs)
                    results.add("Queued: '$step'")
                }
            }
            return results.joinToString(" | ")
        }

        return executeCommand(appContext, trimmed, true)
    }

    private fun executeCommand(
        context: Context,
        rawCommand: String,
        recordForRepeat: Boolean
    ): String {
        val command = rawCommand.trim().lowercase()
        if (command.isEmpty()) return "Kuch sunai nahi diya bhai."

        if (command == "repeat" || command == "phir se" || command == "dobara") {
            val previous = lastRepeatableCommand
                ?: return "Abhi tak koi pichla action command record nahi hua hai."
            return executeCommand(context, previous, false)
        }

        val isRepeatableAction = command.startsWith("open ") ||
            command.startsWith("call ") ||
            command.startsWith("dial ") ||
            command.startsWith("tap ") ||
            command.startsWith("click ") ||
            command.startsWith("find ") ||
            command.startsWith("type ") ||
            command.startsWith("write ") ||
            command.startsWith("search ") ||
            command.contains("scroll down") ||
            command.contains("neeche scroll") ||
            command.contains("scroll up") ||
            command.contains("upar scroll") ||
            command.contains("go back") ||
            command == "back" ||
            command.contains("peeche ja")

        if (recordForRepeat && isRepeatableAction) {
            lastRepeatableCommand = rawCommand.trim()
        }

        return when {
            command == "clear log" || command == "clear chat" || command == "reset log" || command == "clear" -> {
                CLEAR_LOG_SIGNAL
            }
            command == "hello" || command == "hi" || command == "hey" || command.startsWith("namaste") -> {
                "Haan boss, SAMAR hazir hai. Bol kya karna hai?"
            }
            command.contains("who are you") || command.contains("kaun hai tu") || command.contains("intro") -> {
                "Main SAMAR hoon—JARVIS ki classy precision aur Deadpool ka thoda sa attitude."
            }
            command == "help" || command == "commands" -> getCommandsHelp()
            command == "status" || command.contains("system check") -> getStatusSummary(context)
            command.startsWith("call ") || command.startsWith("dial ") -> {
                openDialer(context, rawCommand.trim().substringAfter(" ", "").trim())
            }
            command.startsWith("tap ") -> {
                val coords = command.removePrefix("tap ").trim().split(Regex("[,\\s]+"))
                val x = coords.getOrNull(0)?.toFloatOrNull()
                val y = coords.getOrNull(1)?.toFloatOrNull()
                if (x != null && y != null) tapScreen(x, y) else "Format: tap <x> <y>"
            }
            command.startsWith("sms ") || command.startsWith("send sms ") -> {
                val payload = rawCommand.trim()
                    .replaceFirst(Regex("(?i)^send\\s+sms\\s+|^sms\\s+"), "")
                    .trim()
                val separatorIndex = payload.indexOf(':')
                if (separatorIndex > 0) {
                    val number = payload.substring(0,
