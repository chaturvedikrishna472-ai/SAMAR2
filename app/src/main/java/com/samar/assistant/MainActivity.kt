package com.samar.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var listenButton: Button
    private lateinit var smsSwitch: Switch
    private lateinit var cameraSwitch: Switch
    private lateinit var accessibilitySwitch: Switch

    private lateinit var voiceSpeaker: VoiceSpeaker
    private lateinit var commandRouter: SamarCommandRouter
    private var isUpdatingSwitches = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        syncPermissionSwitches()
    }

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()

            if (!spokenText.isNullOrBlank()) {
                val reply = commandRouter.handleInput(spokenText, CommandContext(source = "voice"))
                resultText.text = "Tum: $spokenText\n\nSAMAR: $reply"
            } else {
                resultText.text = "Awaaz clear nahi aayi, dobara bol bhai."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        listenButton = findViewById(R.id.listenButton)
        smsSwitch = findViewById(R.id.smsPermissionSwitch)
        cameraSwitch = findViewById(R.id.cameraPermissionSwitch)
        accessibilitySwitch = findViewById(R.id.accessibilitySwitch)

        statusText.text = "SAMAR Initializing..."

        voiceSpeaker = VoiceSpeaker(this) { ready ->
            runOnUiThread {
                if (ready) {
                    statusText.text = "SAMAR Online"
                    val greeting = SamarPersona.onReadyLine()
                    resultText.text = greeting
                    voiceSpeaker.speak(greeting)
                } else {
                    statusText.text = "SAMAR Ready (TTS Limited)"
                }
            }
        }

        val smsMessenger = SmsMessenger(this)
        commandRouter = SamarCommandRouter(voiceSpeaker, smsMessenger)

        setupPermissionSwitches()
        syncPermissionSwitches()

        listenButton.setOnClickListener {
            launchVoiceInput()
        }
    }

    override fun onResume() {
        super.onResume()
        syncPermissionSwitches()
    }

    private fun setupPermissionSwitches() {
        smsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitches) return@setOnCheckedChangeListener
            if (isChecked && !SamarPermissions.hasSmsPermission(this)) {
                permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
            }
            syncPermissionSwitches()
        }

        cameraSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitches) return@setOnCheckedChangeListener
            if (isChecked && !SamarPermissions.hasCameraPermission(this)) {
                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            }
            syncPermissionSwitches()
        }

        accessibilitySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitches) return@setOnCheckedChangeListener
            if (isChecked && !SamarPermissions.isAccessibilityEnabled(this)) {
                startActivity(SamarPermissions.openAccessibilitySettingsIntent())
            }
            syncPermissionSwitches()
        }
    }

    private fun syncPermissionSwitches() {
        isUpdatingSwitches = true
        smsSwitch.isChecked = SamarPermissions.hasSmsPermission(this)
        cameraSwitch.isChecked = SamarPermissions.hasCameraPermission(this)
        accessibilitySwitch.isChecked = SamarPermissions.isAccessibilityEnabled(this)
        isUpdatingSwitches = false
    }

    private fun launchVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("hi", "IN"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Bol bhai, SAMAR sun raha hai...")
        }
        speechLauncher.launch(intent)
    }

    override fun onDestroy() {
        voiceSpeaker.shutdown()
        super.onDestroy()
    }
}
