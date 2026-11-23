package com.samar.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import java.util.Locale

data class CommandContext(
    val source: String = "voice"
)

object SamarPersona {
    fun onReadyLine(): String {
        return "SAMAR online, boss. Calm aur crisp mode mein—batao aaj kiski kheer pakani hai?"
    }

    fun replyFor(rawInput: String): String {
        val clean = rawInput.trim()
        return "JARVIS-calm tone mein sun liya: \"$clean\". Filhaal core engine set hai, aage ka action jodne ke liye ready."
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

    fun speak(text: String) {
        if (isReady) {
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
        voiceSpeaker.speak(reply)
        return reply
    }
}
