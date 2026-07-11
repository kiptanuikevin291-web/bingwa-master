package com.bingwamaster.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bingwamaster.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshLog()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        Toast.makeText(
            this,
            if (allGranted) "Permissions granted" else "Some permissions were denied — the watcher won't work fully without them",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        loadPrefsIntoUi()
        refreshLog()

        binding.btnGrantPermissions.setOnClickListener { requestNeededPermissions() }
        binding.btnSave.setOnClickListener { saveUiIntoPrefs() }
        binding.btnTestDial.setOnClickListener { testDial() }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            logReceiver, IntentFilter(SmsReceiver.ACTION_LOG_UPDATED)
        )
        refreshLog()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver)
    }

    private fun loadPrefsIntoUi() {
        binding.etTillNumber.setText(prefs.tillNumber)
        binding.etSenderFilter.setText(prefs.senderFilter)
        binding.etUssdTemplate.setText(prefs.ussdTemplate)
        binding.switchSilent.isChecked = prefs.autoDialSilently
        binding.switchEnabled.isChecked = prefs.watcherEnabled
    }

    private fun saveUiIntoPrefs() {
        prefs.tillNumber = binding.etTillNumber.text.toString()
        prefs.senderFilter = binding.etSenderFilter.text.toString().ifBlank { "MPESA" }
        prefs.ussdTemplate = binding.etUssdTemplate.text.toString()
        prefs.autoDialSilently = binding.switchSilent.isChecked
        prefs.watcherEnabled = binding.switchEnabled.isChecked

        if (prefs.watcherEnabled) {
            if (!hasAllPermissions()) {
                Toast.makeText(this, "Grant permissions first so the watcher can actually work", Toast.LENGTH_LONG).show()
            } else {
                ContextCompat.startForegroundService(this, Intent(this, WatcherForegroundService::class.java))
            }
        } else {
            stopService(Intent(this, WatcherForegroundService::class.java))
        }

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun testDial() {
        val template = binding.etUssdTemplate.text.toString()
        if (template.isBlank()) {
            Toast.makeText(this, "Enter a USSD code first", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "CALL_PHONE permission needed", Toast.LENGTH_SHORT).show()
            return
        }
        val code = UssdHelper.buildCode(template, "100")
        UssdHelper.dial(this, code, binding.switchSilent.isChecked) { success, message ->
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                prefs.appendLog(if (success) "[test] OK: $message" else "[test] FAILED: $message")
                refreshLog()
            }
        }
    }

    private fun refreshLog() {
        val log = prefs.getLog()
        binding.tvLog.text = log.ifBlank { "No activity yet." }
    }

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms.toTypedArray()
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNeededPermissions() {
        permissionLauncher.launch(requiredPermissions())
    }
}
