package com.samar.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tvStatus: TextView? = null
    private var tvLog: TextView? = null
    private var etCommand: EditText? = null
    private var btnSend: Button? = null
    private var btnVoice: Button? = null
    private var btnAccessibility: Button? = null

    private val logBuffer = StringBuilder()
    private var tts: TextToSpeech? = null
    private var isTtsReady: Boolean = false

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                appendLogAndSpeak("You", spoken, speakText = false)
                dispatchUserCommand(spoken)
            } else {
                appendLogAndSpeak("SAMAR", "Aawaz clear nahi aayi bhai, phir se try karo.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)
        etCommand = findViewById(R.id.etCommand)
        btnSend = findViewById(R.id.btnSend)
        btnVoice = findViewById(R.id.btnVoice)
        btnAccessibility = findViewById(R.id.btnAccessibility)

        tts = TextToSpeech(this, this)

        ActivityCompat.requestPermissions(
            this,
            SamarCore.requiredRuntimePermissions.toTypedArray(),
            101
        )

        btnSend?.setOnClickListener {
            val text = etCommand?.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                etCommand?.setText("")
                appendLogAndSpeak("You", text, speakText = false)
                dispatchUserCommand(text)
            }
        }

        btnVoice?.setOnClickListener {
            launchVoiceInput()
        }

        btnAccessibility?.setOnClickListener {
            SamarCore.openAccessibilitySettings(this)
        }

        refreshStatusBanner()
        appendLog("SAMAR", "SAMAR initializing...")
    }

    override fun onResume() {
        super.onResume()
        refreshStatusBanner()
    }

    override fun onInit(status: Int) {
        isTtsReady = status == TextToSpeech.SUCCESS
        if (isTtsReady) {
            val langResult = tts?.setLanguage(Locale("hi", "IN"))
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.US
            }
            speak("SAMAR online. Bol boss, aaj kiski shaamat aayi hai?")
        }
    }

    private fun refreshStatusBanner() {
        val accState = if (SamarCore.isAccessibilityEnabled()) "ON" else "OFF"
        tvStatus?.text = "SAMAR Core Ready | Accessibility: $accState"
    }

    private fun launchVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "SAMAR sun raha hai...")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
            appendLogAndSpeak("SAMAR", "Device par voice recognizer available nahi hai.")
        }
    }

    private fun dispatchUserCommand(rawInput: String) {
        val reply = SamarCore.handleCommand(
            context = this,
            rawCommand = rawInput,
            onAsyncStepResult = { asyncReply ->
                runOnUiThread {
                    appendLogAndSpeak("SAMAR", asyncReply)
                    refreshStatusBanner()
                }
            }
        )
        appendLogAndSpeak("SAMAR", reply)
        refreshStatusBanner()
    }

    private fun appendLog(speaker: String, message: String) {
        if (logBuffer.isNotEmpty()) {
            logBuffer.append("\n\n")
        }
        logBuffer.append("$speaker: $message")
        tvLog?.text = logBuffer.toString()
    }

    private fun appendLogAndSpeak(speaker: String, message: String, speakText: Boolean = true) {
        appendLog(speaker, message)
        if (speakText && speaker == "SAMAR") {
            speak(message)
        }
    }

    private fun speak(text: String) {
        if (isTtsReady && text.isNotBlank()) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "samar_${System.nanoTime()}")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
