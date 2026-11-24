package com.samar.assistant

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var hudSubStatusText: TextView
    private lateinit var hudLastCommandText: TextView
    private lateinit var hudResponseText: TextView
    private lateinit var hudActionLogText: TextView
    private lateinit var hudLatencyText: TextView
    private lateinit var hudCommandInput: EditText
    private lateinit var btnAccessibility: Button
    private lateinit var btnClearHud: Button
    private lateinit var btnMicTrigger: Button
    private lateinit var btnSendCommand: Button

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val spokenText = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    .orEmpty()

                if (spokenText.isNotBlank()) {
                    hudCommandInput.setText(spokenText)
                    executeDirective(spokenText, source = "VOICE")
                } else {
                    updateLog("SYS.LOG: Voice input empty")
                }
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshHudStatus()
            updateLog("SYS.LOG: Permissions updated")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindHudViews()
        wireHudControls()
        requestMissingPermissions()
        refreshHudStatus()
        hudResponseText.text =
            "Systems wired, suit's sharp, no blue glow needed—now let's see if SAMAR actually runs the show or just roasts your typing."
    }

    override fun onResume() {
        super.onResume()
        refreshHudStatus()
    }

    private fun bindHudViews() {
        hudSubStatusText = findViewById(R.id.hudSubStatusText)
        hudLastCommandText = findViewById(R.id.hudLastCommandText)
        hudResponseText = findViewById(R.id.hudResponseText)
        hudActionLogText = findViewById(R.id.hudActionLogText)
        hudLatencyText = findViewById(R.id.hudLatencyText)
        hudCommandInput = findViewById(R.id.hudCommandInput)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnClearHud = findViewById(R.id.btnClearHud)
        btnMicTrigger = findViewById(R.id.btnMicTrigger)
        btnSendCommand = findViewById(R.id.btnSendCommand)
    }

    private fun wireHudControls() {
        btnSendCommand.setOnClickListener {
            val raw = hudCommandInput.text?.toString()?.trim().orEmpty()
            if (raw.isNotEmpty()) {
                executeDirective(raw, source = "HUD")
            } else {
                hudResponseText.text = "Blank input? Bold move. Type an actual directive."
                updateLog("SYS.LOG: Ignored empty directive")
            }
        }

        hudCommandInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                btnSendCommand.performClick()
                true
            } else {
                false
            }
        }

        btnMicTrigger.setOnClickListener {
            launchVoiceInput()
        }

        btnAccessibility.setOnClickListener {
            updateLog("SYS.LOG: Opening Accessibility settings")
            SamarCore.openAccessibilitySettings(this)
        }

        btnClearHud.setOnClickListener {
            resetHudStream("HUD wiped clean. Try not to make a mess this time.")
        }
    }

    private fun executeDirective(command: String, source: String) {
        val startTime = System.currentTimeMillis()
        hudLastCommandText.text = "> [$source] $command"
        hudCommandInput.setText("")

        val reply = SamarCore.handleCommand(
            context = this,
            rawCommand = command,
            onAsyncStepResult = { asyncReply ->
                runOnUiThread {
                    if (asyncReply == SamarCore.CLEAR_LOG_SIGNAL) {
                        resetHudStream("HUD wiped clean. Try not to make a mess this time.")
                    } else {
                        hudResponseText.text = asyncReply
                        refreshHudStatus()
                        updateLog("SYS.LOG: Async step complete")
                    }
                }
            }
        )

        val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
        hudLatencyText.text = "${elapsed}ms"

        if (reply == SamarCore.CLEAR_LOG_SIGNAL) {
            resetHudStream("HUD wiped clean. Try not to make a mess this time.")
            return
        }

        hudResponseText.text = reply
        refreshHudStatus()
        updateLog("SYS.LOG: Executed via $source")
    }

    private fun resetHudStream(message: String) {
        hudLastCommandText.text = "> AWAITING INPUT"
        hudResponseText.text = message
        hudLatencyText.text = "00ms"
        hudCommandInput.setText("")
        updateLog("SYS.LOG: Telemetry stream cleared")
    }

    private fun launchVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak directive for SAMAR...")
        }
        try {
            updateLog("SYS.LOG: Listening for voice directive")
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            hudResponseText.text = "Speech recognizer isn't available on this device. Manual typing still works."
            updateLog("SYS.LOG: Speech recognizer unavailable")
        }
    }

    private fun requestMissingPermissions() {
        val missing = SamarCore.requiredRuntimePermissions.filter { perm ->
            !SamarCore.hasPermission(this, perm)
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun refreshHudStatus() {
        val accActive = SamarCore.isAccessibilityEnabled()
        hudSubStatusText.text = if (accActive) {
            "CORE ONLINE  |  ACCESSIBILITY: ACTIVE"
        } else {
            "CORE ONLINE  |  ACCESSIBILITY: STANDBY"
        }
    }

    private fun updateLog(entry: String) {
        hudActionLogText.text = entry
    }
}
