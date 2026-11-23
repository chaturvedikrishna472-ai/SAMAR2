package com.samar.assistant

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.util.Locale

data class CommandContext(
    val source: String = "voice",
    val voiceReplyEnabled: Boolean = true
)

class SamarAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // Pure phone ki screen events ke liye base hook
    }

    override fun onInterrupt() {
        // Interrupt hook
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    companion object {
        @Volatile
        var instance: SamarAccessibilityService? = null
            private set

        fun isConnected(): Boolean = instance != null
    }
}

object SamarPermissions {
    fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isAccessibilityEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService> = SamarAccessibilityService::class.java
    ): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expectedComponent = "${context.packageName}/${serviceClass.name}"
        return enabledServices
            .split(":")
            .any { it.equals(expectedComponent, ignoreCase = true) }
    }

    fun openAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

object SamarPersona {
    fun onReadyLine(): String {
        return "SAMAR online, boss. Calm aur crisp mode mein—bol kya kaam nikalna hai?"
    }

    fun replyFor(rawInput: String): String {
        val clean = rawInput.trim()
        val lower = clean.lowercase(Locale.ROOT)

        return when {
            lower.contains("stop") || lower.contains("ruk ja") ->
                "Samajh gaya boss, action stop mode ready."
            lower.contains("status") || lower.contains("kya haal") ->
                "Engine smooth chal raha hai, boss. Voice command mode active."
            else ->
                "Sun liya: \"$clean\". Command router ready hai."
        }
    }
}

class VoiceSpeaker(
    private val context: Context,
    private val onReady: (Boolean) -> Unit
) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                tts?.language = Locale("hi", "IN")
            }
            onReady(isReady)
        }
    }

    fun speak(text: String, enabled: Boolean = true) {
        if (enabled && isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SAMAR_UTTERANCE")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

class SmsMessenger(
    private val context: Context
) {
    fun sendSms(phoneNumber: String, message: String): Boolean {
        if (!SamarPermissions.hasSmsPermission(context)) return false
        return try {
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }
}

class SamarCommandRouter(
    private val voiceSpeaker: VoiceSpeaker,
    private val smsMessenger: SmsMessenger
) {
    fun handleInput(rawInput: String, context: CommandContext): String {
        val reply = SamarPersona.replyFor(rawInput)
        voiceSpeaker.speak(reply, enabled = context.voiceReplyEnabled)
        return reply
    }
}
