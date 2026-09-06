# Phase 5: Reliability, Logs UI & Hardening

## Objective
Finalize background reliability for 24/7 continuous operation across aggressive Android OEM battery managers, implement the real-time message audit log UI with retry actions, and execute complete end-to-end verification.

---

## 1. Foreground Service (`SmsForwarderService.kt`)

To ensure the app process survives Android's low-memory killer and Doze mode:
- **Foreground Service Type**: `dataSync` (Android 14+ / API 34+ requirement).
- **Persistent Notification**:
  - Title: *"SMS Forwarder Active"*
  - Content: *"Monitoring SIM 1 & SIM 2 • Forwarding to Telegram/WhatsApp"*
  - Quick Action Button: *"Pause / Resume"*

### Android 14+ Compliant Start
```kotlin
class SmsForwarderService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }
}
```

---

## 2. Auto-Restart & Battery Optimization Handling

### Device Reboot Survival (`BootReceiver.kt`)
- Intercepts `Intent.ACTION_BOOT_COMPLETED`.
- Reads `isServiceEnabled` from `AppPreferences`.
- If enabled, restarts `SmsForwarderService` in foreground mode immediately.

### Battery Optimization Exemption
- Aggressive manufacturers (Samsung OneUI, Xiaomi MIUI, Huawei) kill background apps aggressively.
- In Settings, provide a guided button to request exemption from battery optimization:
  ```kotlin
  val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
      data = Uri.parse("package:$packageName")
  }
  context.startActivity(intent)
  ```

---

## 3. Logs & Audit UI (`LogsScreen.kt`)

A dedicated audit tab to inspect every received message:
- **Header Summary**: Total Received, Forwarded Successfully, Failed, Skipped.
- **Log Item Row**:
  - **SIM Slot Badge**: `SIM 1 (T-Mobile)` in blue, `SIM 2 (Vodafone)` in green.
  - **Sender & Time**: `+1 (555) 019-2831` • `14:32:10`.
  - **Destination Badges**:
    - `Telegram: ✓ Sent` (or `✗ HTTP 400`)
    - `WhatsApp: ✓ Sent` (or `⚠ Blocked (OTP)`)
  - **Snippet**: First 2 lines of the SMS.
- **Actions**:
  - Tap row to expand full message body and error stack trace (if any).
  - **"Retry Forwarding"** button on failed items to re-dispatch via `ForwardWorker`.
  - Filter bar: Filter by SIM 1 / SIM 2 / Failed.
  - Clear history button.

---

## 4. End-to-End Test Suite

### Testing Scenarios
1. **Dual-SIM Filter Test**:
   - Set SIM Filter to `SIM 1 Only`.
   - Send SMS to SIM 2 → App logs as `SKIPPED (SIM Filtered)`. No message sent to Telegram/WhatsApp.
   - Send SMS to SIM 1 → App forwards immediately to Telegram & WhatsApp.
2. **Privacy Guard Test**:
   - Send SMS: `"Your verification code is 849201 for bank login."`
   - Telegram receives full message.
   - CallMeBot is skipped or receives `[REDACTED]` code based on settings.
3. **Offline Resilience Test**:
   - Enable Airplane Mode.
   - Send SMS to device.
   - App logs SMS as `PENDING`.
   - Disable Airplane Mode.
   - WorkManager detects network connectivity and forwards queued message within seconds.
4. **Reboot Test**:
   - Reboot device.
   - Confirm `SmsForwarderService` notification automatically reappears.
