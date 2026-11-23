package com.samar.assistant

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var etCommand: EditText

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val grantedCount = results.values.count { it }
        updateStatus("Permissions updated ($grantedCount/${results.size} granted).")
    }

    private val voiceInputLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spokenText.isNotBlank()) {
                etCommand.setText(spokenText)
                val reply = SamarCore.handleCommand(this, spokenText)
                updateStatus(reply)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        etCommand = findViewById(R.id.etCommand)

        findViewById<Button>(R.id.btnPermissions).setOnClickListener {
            requestPermissionsLauncher.launch(SamarCore.requiredRuntimePermissions.toTypedArray())
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            SamarCore.openAccessibilitySettings(this)
        }

        findViewById<Button>(R.id.btnVoice).setOnClickListener {
            startVoiceRecognition()
        }

        findViewById<Button>(R.id.btnSend).setOnClickListener {
            val text = etCommand.text.toString()
            val reply = SamarCore.handleCommand(this, text)
            updateStatus(reply)
        }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Bolo SAMAR ko...")
        }
        try {
            voiceInputLauncher.launch(intent)
        } catch (_: Exception) {
            updateStatus("Voice recognizer available nahi hai, text command use karo.")
        }
    }

    private fun refreshStatus() {
        val accStatus = if (SamarCore.isAccessibilityEnabled()) "ON" else "OFF"
        updateStatus("SAMAR ready | Accessibility: $accStatus")
    }

    private fun updateStatus(message: String) {
        tvStatus.text = message
    }
}
