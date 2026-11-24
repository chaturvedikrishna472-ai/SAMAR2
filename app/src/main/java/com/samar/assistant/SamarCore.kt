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
        return "SAMAR status: Accessibility $acc, Mic $mic, SMS $sms. Main taiyar hoon, ab tu apna plan mat bigaadna."
    }

    fun getCommandsHelp(): String {
        return "Commands: open <app/settings/wifi/bluetooth/sound/battery/display/location>, call <number>, click <text>, find <text>, tap <x> <y>, type <text>, search <text>, sms <number> : <msg>, read screen, scroll down/up, back, home, recents, repeat, clear log, status. 'and' ya 'then' se chain bhi kar sakte ho."
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
            ?: return "Accessibility service off hai boss. Pehle settings mein on karo, warna main hawa mein tap karunga."
        val ok = service.performTap(x, y)
        return if (ok) {
            "($x, $y) par tap kar diya hai, bilkul surgical precision ke saath."
        } else {
            "Tap dispatch fail ho gaya—screen shayad nakhre dikha rahi hai."
        }
    }

    fun clickText(targetText: String): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo tabhi button daba paunga."
        val cleanTarget = targetText.trim()
        if (cleanTarget.isEmpty()) {
            return "Kya click karna hai uska naam toh batao, main man ki baat nahi padh sakta abhi."
        }
        val ok = service.clickNodeByText(cleanTarget)
        return if (ok) {
            "'$cleanTarget' par click kar diya hai. Aage kya tabahi machani hai?"
        } else {
            "Screen par '$cleanTarget' nahi mila ya clickable nahi hai."
        }
    }

    fun findOrClickText(targetText: String): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        val cleanTarget = targetText.trim()
        if (cleanTarget.isEmpty()) {
            return "Screen par kya dhoondhna hai wo toh batao."
        }
        if (service.clickNodeByText(cleanTarget)) {
            return "'$cleanTarget' mil gaya aur click kar diya hai."
        }
        val screenContent = service.readScreenText(40)
        return if (screenContent.contains(cleanTarget, ignoreCase = true)) {
            "'$cleanTarget' screen par dikh raha hai, par direct clickable node nahi mila."
        } else {
            "'$cleanTarget' abhi is screen par nahi mila—'scroll down' try karo."
        }
    }

    fun typeIntoField(text: String): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo tabhi type kar paunga."
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return "Kya type karna hai wo toh batao."
        }
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
        if (cleanQuery.isEmpty()) {
            return "Kya search karna hai wo toh bolo."
        }
        service.clickNodeByText("Search")
        return if (service.typeText(cleanQuery)) {
            "'$cleanQuery' search field mein daal diya hai."
        } else {
            "Search box nahi mil paaya—pehle field tap karke 'type $cleanQuery' try karo."
        }
    }

    fun goBack(): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        return if (service.performSystemBack()) {
            "Ek kadam peeche—just like a tactical retreat."
        } else {
            "Back action nahi ho paaya."
        }
    }

    fun goHome(): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        return if (service.performSystemHome()) {
            "Home screen par wapas. Mission accomplished."
        } else {
            "Home action nahi ho paaya."
        }
    }

    fun openRecents(): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        return if (service.performSystemRecents()) {
            "Recent apps khol di hain—dekho kaun kaun line mein khada hai."
        } else {
            "Recent apps nahi khul paayin."
        }
    }

    fun scrollScreen(down: Boolean = true): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo tabhi scroll ho paayega."
        val ok = service.performScroll(down)
        val direction = if (down) "neeche" else "upar"
        return if (ok) {
            "Screen $direction scroll kar di hai."
        } else {
            "Screen scroll nahi ho paayi."
        }
    }

    fun readScreen(maxItems: Int = 25): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo tabhi screen padh paunga."
        return service.readScreenText(maxItems)
    }

    fun sendSms(context: Context, phoneNumber: String, message: String): String {
        if (!hasPermission(context, Manifest.permission.SEND_SMS)) {
            return "SEND_SMS permission nahi mili—bina permission ke main SMS nahi bhej sakta."
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
        if (trimmed.isEmpty()) {
            return "Kuch sunai nahi diya bhai, mic ke saamne thoda aur zor se bolo."
        }

        val steps = trimmed.split(Regex("(?i)\\s+(?:and|then)\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (steps.size > 1) {
            val results = mutableListOf<String>()
            steps.forEachIndexed { index, step ->
                if (index == 0) {
                    results.add(handleSingleCommand(appContext, step, recordForRepeat = true))
                } else {
                    val delayMs = index * 1100L
                    mainHandler.postDelayed(Runnable {
                        val stepReply = handleSingleCommand(appContext, step, recordForRepeat = false)
                        onAsyncStepResult?.invoke(stepReply)
                    }, delayMs)
                    results.add("Queued: '$step'")
                }
            }
            return results.joinToString(" | ")
        }

        return handleSingleCommand(appContext, trimmed, recordForRepeat = true)
    }

    private fun handleSingleCommand(
        context: Context,
        rawCommand: String,
        recordForRepeat: Boolean
    ): String {
        val command = rawCommand.trim().lowercase()
        if (command.isEmpty()) {
            return "Kuch sunai nahi diya bhai, mic ke saamne thoda aur zor se bolo."
        }

        if (command == "repeat" || command == "phir se" || command == "dobara") {
            val previous = lastRepeatableCommand
                ?: return "Abhi tak koi pichla action command record nahi hua hai."
            return handleSingleCommand(context, previous, recordForRepeat = false)
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
                "Haan boss, SAMAR hazir hai—suit polished aur sarcasm loaded. Bol kya karna hai?"
            }
            command.contains("who are you") || command.contains("kaun hai tu") || command.contains("intro") -> {
                "Main SAMAR hoon—JARVIS ki classy precision aur Deadpool ka thoda sa be-adab attitude, ek hi Android body mein."
            }
            command == "help" || command == "commands" || command.contains("kya kar sakte") -> {
                getCommandsHelp()
            }
            command == "status" || command.contains("system check") -> {
                getStatusSummary(context)
            }
            command.startsWith("call ") || command.startsWith("dial ") -> {
                val number = rawCommand.trim().substringAfter(" ", "").trim()
                openDialer(context, number)
            }
            command.startsWith("tap ") -> {
                val coords = command.removePrefix("tap ").trim().split(Regex("[,\\s]+"))
                val x = coords.getOrNull(0)?.toFloatOrNull()
                val y = coords.getOrNull(1)?.toFloatOrNull()
                if (x != null && y != null) {
                    tapScreen(x, y)
                } else {
                    "Format aisa rakho: tap <x> <y> (jaise: tap 540 1200)."
                }
            }
            command.startsWith("sms ") || command.startsWith("send sms ") -> {
                val payload = rawCommand.trim()
                    .replaceFirst(Regex("(?i)^send\\s+sms\\s+|^sms\\s+"), "")
                    .trim()
                val separatorIndex = payload.indexOf(':')
                if (separatorIndex > 0) {
                    val number = payload.substring(0, separatorIndex).trim()
                    val msg = payload.substring(separatorIndex + 1).trim()
                    if (number.isNotEmpty() && msg.isNotEmpty()) {
                        sendSms(context, number, msg)
                    } else {
                        "Format aisa rakho: sms <number> : <message>"
                    }
                } else {
                    val parts = payload.split(Regex("\\s+"), limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                        sendSms(context, parts[0].trim(), parts[1].trim())
                    } else {
                        "Format aisa rakho: sms <number> : <message>"
                    }
                }
            }
            command.startsWith("search ") -> {
                val query = rawCommand.trim().substringAfter(" ", "").trim()
                searchOnScreen(query)
            }
            command.startsWith("type ") || command.startsWith("write ") -> {
                val textToType = rawCommand.trim().substringAfter(" ", "").trim()
                typeIntoField(textToType)
            }
            command.startsWith("find ") -> {
                val target = rawCommand.trim().substringAfter(" ", "").trim()
                findOrClickText(target)
            }
            command.startsWith("click ") -> {
                val target = rawCommand.trim().substringAfter(" ", "").trim()
                clickText(target)
            }
            command.contains("go back") || command == "back" || command.contains("peeche ja") -> {
                goBack()
            }
            command.contains("go home") || command == "home" || command.contains("home screen") -> {
                goHome()
            }
            command.contains("recent") || command.contains("recents") -> {
                openRecents()
            }
            command.contains("scroll down") || command.contains("neeche scroll") -> {
                scrollScreen(down = true)
            }
            command.contains("scroll up") || command.contains("upar scroll") -> {
                scrollScreen(down = false)
            }
            command.contains("read screen") || command.contains("screen padh") -> {
                readScreen()
            }
            command.contains("accessibility") -> {
                openAccessibilitySettings(context)
                "Accessibility settings khol di hain, ab SAMAR ko full power do."
            }
            command.startsWith("open ") -> {
                val target = command.removePrefix("open ").trim()
                when (target) {
                    "settings" -> {
                        context.startActivity(
                            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        "Phone settings khol di hain—kuch bigaadna mat."
                    }
                    "wifi" -> {
                        context.startActivity(
                            Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        "Wi-Fi settings khol di hain."
                    }
                    "bluetooth" -> {
                        context.startActivity(
                            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        "Bluetooth settings khol di hain."
                    }
                    "sound" -> {
                        context.startActivity(
                            Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        "Sound settings khol di hain."
                    }
                    "battery" -> {
                        context.startActivity(
                            Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        "Battery usage settings khol di hain."
                    }
                    "display" -> {
                        context.startActivity(
                            Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        "Display settings khol di hain."
                    }
                    "location" -> {
                        context.startActivity(
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        "Location settings khol di hain."
                    }
                    else -> when {
                        commonAppPackages.containsKey(target) -> {
                            openAppByPackage(context, commonAppPackages.getValue(target))
                        }
                        target.contains(".") -> {
                            openAppByPackage(context, target)
                        }
                        else -> {
                            "'$target' ka exact package nahi pehchana—ya toh package naam do ya common app (whatsapp, youtube, chrome, camera, instagram, maps, clock, contacts, gallery, photos) bolo."
                        }
                    }
                }
            }
            else -> {
                "SAMAR sun raha hai—'$rawCommand' bola tune. 'help' bol kar commands dekh lo ya seedha action command do."
            }
        }
    }
}
