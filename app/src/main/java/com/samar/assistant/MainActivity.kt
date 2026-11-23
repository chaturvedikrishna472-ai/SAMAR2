package com.samar.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var listenButton: Button

    private lateinit var voiceSpeaker: VoiceSpeaker
    private lateinit var smsMessenger: SmsMessenger
    private lateinit var commandRouter: SamarCommandRouter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted) {
            launchVoiceRecognizer()
        } else {
            resultText.text = "Mic permission denied."
        }
    }

    private val voiceRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()?.trim().orEmpty()
            if (spokenText.isNotEmpty()) {
                val reply = commandRouter.handleInput(spokenText, CommandContext(source = "voice"))
                resultText.text = "> $spokenText\n$reply"
            } else {
                resultText.text = "Awaaz clear nahi aayi."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        listenButton = findViewById(R.id.listenButton)

        statusText.text = "SAMAR Ready"

        voiceSpeaker = VoiceSpeaker(this) { ready ->
            runOnUiThread {
                if (ready) {
                    val greeting = SamarPersona.onReadyLine()
                    statusText.text = "SAMAR Online"
                    resultText.text = greeting
                    voiceSpeaker.speak(greeting)
                } else {
                    statusText.text = "TTS Unavailable"
                    resultText.text = "TTS ready nahi ho paaya."
                }
            }
        }

        smsMessenger = SmsMessenger(this)
        commandRouter = SamarCommandRouter(voiceSpeaker, smsMessenger)

        listenButton.setOnClickListener {
            checkAudioPermissionAndListen()
        }
    }

    private fun checkAudioPermissionAndListen() {
        val hasAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudio) {
            launchVoiceRecognizer()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.SEND_SMS
                )
            )
        }
    }

    private fun launchVoiceRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("hi", "IN"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "SAMAR ko order de...")
        }
        try {
            voiceRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            resultText.text = "Speech recognizer open nahi hua: ${e.message}"
        }
    }

    override fun onDestroy() {
        voiceSpeaker.shutdown()
        super.onDestroy()
    }
}
