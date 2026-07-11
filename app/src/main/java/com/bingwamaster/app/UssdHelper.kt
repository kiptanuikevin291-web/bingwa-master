package com.bingwamaster.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Dials a USSD code, e.g. "*544*1*500#".
 *
 * Preferred path (API 26+): TelephonyManager.sendUssdRequest — runs the session
 * without showing any dialer UI and delivers the network's response back to us.
 * This is NOT supported on every device/carrier combination (some OEM firmwares
 * block it), so we fall back to launching the system dialer with the code
 * pre-filled, which still requires the user (or an Accessibility Service, not
 * included here) to tap Call.
 */
object UssdHelper {

    private const val TAG = "UssdHelper"

    fun buildCode(template: String, amount: String?): String {
        return if (amount != null) template.replace("{amount}", amount) else template
    }

    fun dial(
        context: Context,
        ussdCode: String,
        preferSilent: Boolean,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CALL_PHONE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasCallPermission) {
            onResult(false, "CALL_PHONE permission not granted")
            return
        }

        val encoded = encodeUssd(ussdCode)

        if (preferSilent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                tm.sendUssdRequest(
                    ussdCode,
                    object : TelephonyManager.UssdResponseCallback() {
                        override fun onReceiveUssdResponse(
                            telephonyManager: TelephonyManager,
                            request: String,
                            response: CharSequence
                        ) {
                            onResult(true, "USSD response: $response")
                        }

                        override fun onReceiveUssdResponseFailed(
                            telephonyManager: TelephonyManager,
                            request: String,
                            failureCode: Int
                        ) {
                            Log.w(TAG, "Silent USSD failed ($failureCode), falling back to dialer")
                            Handler(Looper.getMainLooper()).post {
                                fallbackToDialer(context, encoded, onResult)
                            }
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (e: SecurityException) {
                onResult(false, "Security exception dialing USSD: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "sendUssdRequest threw, falling back to dialer", e)
                fallbackToDialer(context, encoded, onResult)
            }
        } else {
            fallbackToDialer(context, encoded, onResult)
        }
    }

    private fun fallbackToDialer(
        context: Context,
        encodedUssd: String,
        onResult: (Boolean, String) -> Unit
    ) {
        try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$encodedUssd"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            onResult(true, "Opened dialer with USSD code (silent dial unsupported on this device)")
        } catch (e: Exception) {
            onResult(false, "Failed to open dialer: ${e.message}")
        }
    }

    // '#' must be percent-encoded as %23 inside a tel: URI, or the OS truncates the string.
    private fun encodeUssd(code: String): String = code.replace("#", Uri.encode("#"))
}
