package com.bingwamaster.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin wrapper around SharedPreferences holding all user-configurable settings.
 *
 * tillNumber        - the Safaricom till/paybill number to watch for in incoming SMS.
 * ussdTemplate       - the USSD code to dial when a matching payment SMS arrives.
 *                       Use {amount} as a placeholder if the code needs the paid amount,
 *                       e.g. "*544*1*{amount}#"
 * senderFilter       - comma separated list of SMS sender IDs to trust (defaults to MPESA).
 * watcherEnabled     - master on/off switch for the whole feature.
 * autoDialSilently   - if true, use TelephonyManager.sendUssdRequest (no dialer UI).
 *                       If false / unsupported, falls back to opening the dialer pre-filled.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("bingwa_master_prefs", Context.MODE_PRIVATE)

    var tillNumber: String
        get() = sp.getString(KEY_TILL, "") ?: ""
        set(value) = sp.edit().putString(KEY_TILL, value.trim()).apply()

    var ussdTemplate: String
        get() = sp.getString(KEY_USSD, "") ?: ""
        set(value) = sp.edit().putString(KEY_USSD, value.trim()).apply()

    var senderFilter: String
        get() = sp.getString(KEY_SENDER, "MPESA") ?: "MPESA"
        set(value) = sp.edit().putString(KEY_SENDER, value.trim()).apply()

    var watcherEnabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_ENABLED, value).apply()

    var autoDialSilently: Boolean
        get() = sp.getBoolean(KEY_SILENT, true)
        set(value) = sp.edit().putBoolean(KEY_SILENT, value).apply()

    fun appendLog(line: String) {
        val existing = sp.getString(KEY_LOG, "") ?: ""
        val stamped = "$line\n$existing"
        // Keep only the most recent ~4000 characters so prefs don't grow unbounded.
        sp.edit().putString(KEY_LOG, stamped.take(4000)).apply()
    }

    fun getLog(): String = sp.getString(KEY_LOG, "") ?: ""

    fun clearLog() = sp.edit().putString(KEY_LOG, "").apply()

    companion object {
        private const val KEY_TILL = "till_number"
        private const val KEY_USSD = "ussd_template"
        private const val KEY_SENDER = "sender_filter"
        private const val KEY_ENABLED = "watcher_enabled"
        private const val KEY_SILENT = "auto_dial_silently"
        private const val KEY_LOG = "event_log"
    }
}
