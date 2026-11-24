package com.samar.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var statusView: TextView? = null
    private var logView: TextView? = null
    private var logScroll: ScrollView? = null
    private var commandInput: EditText? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshStatusBanner()
        appendLog("SAMAR: Permissions update ho gayi hain. ${SamarCore.getStatusSummary(this)}")
    }

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                commandInput?.setText(spoken)
                submitCommand(spoken)
            } else {
                appendLog("SAMAR: Awaaz clear nahi aayi, phir se try karo.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layoutId = resources.getIdentifier("activity_main", "layout", packageName)
        if (layoutId != 0) {
            setContentView(layoutId)
            bindViewsIfPresent()
        }

        requestMissingRuntimePermissions()
        refreshStatusBanner()
        appendLog("SAMAR: Online. 'help' bol kar commands check karo ya seedha action do.")
    }

    override fun onResume() {
        super.onResume()
        refreshStatusBanner()
    }

    private fun bindViewsIfPresent() {
        statusView = findId("tvStatus")
        logView = findId("tvLog")
        logScroll = findId("scrollLog")
        commandInput = findId("etCommand")

        findId<Button>("btnSend")?.setOnClickListener {
            val text = commandInput?.text?.toString().orEmpty()
            commandInput?.text?.clear()
            submitCommand(text)
        }

        findId<Button>("btnVoice")?.setOnClickListener {
            startVoiceRecognition()
        }

        findId<Button>("btnAccessibility")?.setOnClickListener {
            SamarCore.openAccessibilitySettings(this)
        }

        findId<Button>("btnClear")?.setOnClickListener {
            clearLog()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> findId(name: String): T? {
        val id = resources.getIdentifier(name, "id", packageName)
        return if (id != 0) findViewById(id) as? T else null
    }

    private fun requestMissingRuntimePermissions() {
        val missing = SamarCore.requiredRuntimePermissions.filter { perm ->
            ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startVoiceRecognition() {
        if (!SamarCore.hasPermission(this, Manifest.permission.RECORD_AUDIO)) {
            requestMissingRuntimePermissions()
            appendLog("SAMAR: Mic permission chahiye pehle.")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Bol boss, SAMAR sun raha hai...")
        }

        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            appendLog("SAMAR: Voice recognizer available nahi hai (${e.message ?: "error"}).")
        }
    }

    fun submitCommand(rawCommand: String) {
        val clean = rawCommand.trim()
        if (clean.isEmpty()) return

        appendLog("You: $clean")
        val reply = SamarCore.handleCommand(this, clean) { asyncReply ->
            runOnUiThread {
                if (asyncReply == SamarCore.CLEAR_LOG_SIGNAL) {
                    clearLog()
                } else {
                    appendLog("SAMAR: $asyncReply")
                }
            }
        }

        if (reply == SamarCore.CLEAR_LOG_SIGNAL) {
            clearLog()
        } else {
            appendLog("SAMAR: $reply")
        }
        refreshStatusBanner()
    }

    private fun refreshStatusBanner() {
        statusView?.text = SamarCore.getStatusSummary(this)
    }

    private fun clearLog() {
        logView?.text = ""
    }

    private fun appendLog(line: String) {
        val current = logView?.text?.toString().orEmpty()
        logView?.text = if (current.isEmpty()) line else "$current\n$line"
        logScroll?.post {
            logScroll?.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}
