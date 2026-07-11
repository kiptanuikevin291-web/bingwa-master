# Bingwa Master

An Android app that watches for M-Pesa till payment confirmation SMS and
automatically dials a USSD code in response (e.g. to top up a bundle, sweep
float, etc).

## How it works

1. `SmsReceiver` listens for incoming SMS (`android.provider.Telephony.SMS_RECEIVED`).
2. When a message from your configured sender (default `MPESA`) contains
   "Confirmed" + "received Ksh...", it's treated as a payment.
3. The app builds your configured USSD code, replacing `{amount}` with the
   paid amount if present, and dials it via `UssdHelper`.
4. `UssdHelper` tries `TelephonyManager.sendUssdRequest` first (Android 8+,
   dials silently with no dialer popup). If that's unsupported on your
   device/carrier, it falls back to opening the phone dialer with the code
   pre-filled — you (or an accessibility service, not included) still have to
   tap Call in that case.
5. A foreground service + notification keep the app alive so aggressive
   battery-optimisation ROMs (common on Tecno/Infinix/itel/Samsung) don't kill
   it before it can react.

## Setup

1. Open the `BingwaMaster` folder in Android Studio (Hedgehog or newer).
   Let it sync Gradle — it will download the wrapper automatically.
2. Build & run on a real device (USSD/SMS don't work in the emulator).
3. In the app:
   - Tap **Grant SMS + Call permissions** and accept all prompts.
   - Enter your till/account number if you want to restrict matching to
     payments mentioning that account (optional — leave blank to match any
     received-money SMS from the sender filter).
   - Set the **SMS sender filter** (default `MPESA` matches Safaricom's
     sender ID).
   - Set the **USSD code to dial**, using `{amount}` where the paid amount
     should go, e.g. `*544*1*{amount}#`.
   - Toggle **Watcher enabled** on and tap **Save settings**.
4. Use **Test dial configured USSD code** to confirm the USSD flow itself
   works correctly before relying on it live.

## Important things to know

- **Silent USSD dialing is not guaranteed.** Some Android builds (especially
  heavily customised OEM firmware) block `sendUssdRequest` for third-party
  apps. Test on your actual device before depending on it.
- **Dual-SIM phones**: `sendUssdRequest` uses the default voice SIM. If your
  till line is on the second SIM, you may need to set it as default for
  calls in Android's SIM settings.
- **SMS format changes**: the parser in `SmsReceiver.parsePayment()` matches
  Safaricom's current till/paybill confirmation wording. If Safaricom changes
  their SMS wording, update the regex there.
- **Consider the official Daraja API**: for anything beyond a personal/small
  shop tool, Safaricom's Daraja API (C2B confirmation callbacks) is the
  sanctioned, far more reliable way to get notified of till payments — no SMS
  parsing or permission fragility involved. This app is the "just watch my
  own phone's SMS" approach, which is simpler to set up but more fragile.
- **Only use this on a phone/till line you own or are authorised to
  automate.** Reading another person's SMS or dialing USSD on their behalf
  without consent would misuse the `READ_SMS`/`CALL_PHONE` permissions this
  app requests.

## Permissions used

| Permission | Why |
|---|---|
| `RECEIVE_SMS` / `READ_SMS` | Detect incoming M-Pesa payment SMS |
| `CALL_PHONE` | Dial the USSD code |
| `FOREGROUND_SERVICE` / `POST_NOTIFICATIONS` | Keep the watcher alive with a visible notification |
| `RECEIVE_BOOT_COMPLETED` | Resume watching after the phone restarts |

## Project structure

```
app/src/main/java/com/bingwamaster/app/
  MainActivity.kt              Settings UI, permissions, test dial, log view
  SmsReceiver.kt                Parses incoming SMS, triggers USSD dial
  UssdHelper.kt                 Silent USSD dial + dialer fallback
  WatcherForegroundService.kt   Keep-alive notification
  BootReceiver.kt               Restart watcher after reboot
  Prefs.kt                      Settings storage
```
