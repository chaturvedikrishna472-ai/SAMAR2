package com.samar.assistant

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.TextUtils
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindHudViews()
        wireHudControls()
        refreshAccessibilityState()
    }

    override fun onResume() {
        super.onResume()
        refreshAccessibilityState()
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
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnClearHud.setOnClickListener {
            hudLastCommandText.text = "> AWAITING INPUT"
            hudResponseText.text = "HUD wiped clean. Try not to make a mess this time."
            hudLatencyText.text = "00ms"
            hudCommandInput.setText("")
            updateLog("SYS.LOG: Telemetry stream cleared")
        }
    }

    private fun executeDirective(command: String, source: String) {
        val startTime = System.currentTimeMillis()
        hudLastCommandText.text = "> [$source] $command"
        hudCommandInput.setText("")

        // TODO: Connect your SamarCore / SamarAccessibilityService execution method here
        val reply = buildTacticalReply(command)
        val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)

        hudResponseText.text = reply
        hudLatencyText.text = "${elapsed}ms"
        updateLog("SYS.LOG: Executed via $source")
    }

    private fun buildTacticalReply(command: String): String {
        val lower = command.lowercase(Locale.getDefault())
        val serviceActive = isAccessibilityEnabled(this, SamarAccessibilityService::class.java)

        return when {
            lower.contains("status") ->
                "Core nominal. Accessibility link: ${if (serviceActive) "ACTIVE" else "OFFLINE"}. Still doing heavy lifting without a blue glow."
            lower.contains("accessibility") ->
                if (serviceActive) {
                    "Accessibility service is already live. I can see what's on screen—behave accordingly."
                } else {
                    "Accessibility is off. Tap ACCESSIBILITY below unless you want me flying blind."
                }
            else ->
                "Directive logged: \"$command\". Core is wired up—hook your backend handler inside SamarCore when ready."
        }
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

    private fun refreshAccessibilityState() {
        val active = isAccessibilityEnabled(this, SamarAccessibilityService::class.java)
        hudSubStatusText.text = if (active) {
            "CORE ONLINE  |  ACCESSIBILITY: ACTIVE"
        } else {
            "CORE ONLINE  |  ACCESSIBILITY: STANDBY"
        }
    }

    private fun updateLog(entry: String) {
        hudActionLogText.text = entry
    }

    private fun isAccessibilityEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService>
    ): Boolean {
        val expectedComponent = ComponentName(context, serviceClass).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedComponent, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
