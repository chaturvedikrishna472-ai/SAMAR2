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

    private val commonAppPackages: Map<String, String> = mapOf(
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "play store" to "com.android.vending",
        "phone" to "com.android.dialer",
        "dialer" to "com.android.dialer",
        "messages" to "com.google.android.apps.messaging",
        "camera" to "com.android.camera"
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
        return "Commands: open <app/settings>, click <text>, type <text>, search <text>, read screen, scroll down/up, back, home, recents, status. 'and' ya 'then' se chain bhi kar sakte ho."
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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

    fun handleCommand(context: Context, rawCommand: String): String {
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
                    results.add(handleSingleCommand(context, step))
                } else {
                    val delayMs = index * 900L
                    mainHandler.postDelayed({
                        handleSingleCommand(context, step)
                    }, delayMs)
                    results.add("Queued: '$step'")
                }
            }
            return results.joinToString(" | ")
        }

        return handleSingleCommand(context, trimmed)
    }

    private fun handleSingleCommand(context: Context, rawCommand: String): String {
        val command = rawCommand.trim().lowercase()
        if (command.isEmpty()) {
            return "Kuch sunai nahi diya bhai, mic ke saamne thoda aur zor se bolo."
        }

        return when {
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
            command.startsWith("search ") -> {
                val query = rawCommand.trim().substringAfter(" ", "").trim()
                searchOnScreen(query)
            }
            command.startsWith("type ") || command.startsWith("write ") -> {
                val textToType = rawCommand.trim().substringAfter(" ", "").trim()
                typeIntoField(textToType)
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
                when {
                    target == "settings" -> {
                        context.startActivity(
                            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        "Phone settings khol di hain—kuch bigaadna mat."
                    }
                    commonAppPackages.containsKey(target) -> {
                        openAppByPackage(context, commonAppPackages.getValue(target))
                    }
                    target.contains(".") -> {
                        openAppByPackage(context, target)
                    }
                    else -> {
                        "'$target' ka exact package nahi pehchana—ya toh package naam do ya common app (whatsapp, youtube, chrome, camera) bolo."
                    }
                }
            }
            else -> {
                "SAMAR sun raha hai—'$rawCommand' bola tune. 'help' bol kar commands dekh lo ya seedha action command do."
            }
        }
    }
}
