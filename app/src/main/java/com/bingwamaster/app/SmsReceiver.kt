package com.bingwamaster.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Fires on every incoming SMS. We only act when:
 *  - the watcher is enabled in settings
 *  - the sender matches the configured filter (e.g. "MPESA")
 *  - the body looks like a till payment CONFIRMATION (money received), not money sent
 *
 * On a match, we build the configured USSD code (optionally inserting the paid
 * amount) and dial it via UssdHelper.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = Prefs(context)
        if (!prefs.watcherEnabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Multi-part SMS: concatenate all parts, take sender from the first.
        val sender = messages[0].displayOriginatingAddress ?: ""
        val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }

        val allowedSenders = prefs.senderFilter.split(",").map { it.trim().uppercase() }
        val senderMatches = allowedSenders.any { it.isNotEmpty() && sender.uppercase().contains(it) }
        if (!senderMatches) return

        val payment = parsePayment(fullBody) ?: return

        if (prefs.tillNumber.isNotBlank()) {
            // If the SMS explicitly mentions a till/account number, make sure it matches.
            val accountRegex = Regex("account\\s+(\\w+)", RegexOption.IGNORE_CASE)
            val accountMatch = accountRegex.find(fullBody)
            if (accountMatch != null && accountMatch.groupValues[1] != prefs.tillNumber) {
                return
            }
        }

        val ussdTemplate = prefs.ussdTemplate
        if (ussdTemplate.isBlank()) {
            log(context, prefs, "Payment of Ksh${payment.amount} detected but no USSD code is configured.")
            return
        }

        val code = UssdHelper.buildCode(ussdTemplate, payment.amount)
        log(context, prefs, "Payment of Ksh${payment.amount} from ${payment.payer} detected. Dialing $code ...")

        UssdHelper.dial(context, code, prefs.autoDialSilently) { success, message ->
            log(context, prefs, if (success) "USSD dial OK: $message" else "USSD dial FAILED: $message")
        }
    }

    private data class Payment(val amount: String, val payer: String)

    /**
     * Matches standard Safaricom till/paybill confirmation SMS, e.g.:
     * "Confirmed. You have received Ksh500.00 from JOHN DOE 254712345678 on ..."
     * Deliberately ignores "sent to" / "paid to" messages (outgoing money).
     */
    private fun parsePayment(body: String): Payment? {
        val lower = body.lowercase()
        if (!lower.contains("confirmed")) return null
        if (!lower.contains("received")) return null

        val amountRegex = Regex("received\\s+ksh([0-9,]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
        val amountMatch = amountRegex.find(body) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "")

        val payerRegex = Regex("from\\s+([A-Za-z .]+?)\\s+(?:\\d{9,}|0\\d{9})", RegexOption.IGNORE_CASE)
        val payer = payerRegex.find(body)?.groupValues?.get(1)?.trim() ?: "unknown"

        return Payment(amount, payer)
    }

    private fun log(context: Context, prefs: Prefs, message: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(System.currentTimeMillis())
        val line = "[$ts] $message"
        prefs.appendLog(line)
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ACTION_LOG_UPDATED))
    }

    companion object {
        const val ACTION_LOG_UPDATED = "com.bingwamaster.app.LOG_UPDATED"
    }
}
