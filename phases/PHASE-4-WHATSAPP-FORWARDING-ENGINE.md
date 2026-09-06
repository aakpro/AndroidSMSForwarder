# Phase 4: WhatsApp Forwarding Engine & Privacy Protections

## Objective
Implement WhatsApp forwarding supporting **Automated Background API (CallMeBot & Custom Webhooks)** and **Interactive Draft Mode (WhatsApp Intent)**, while strictly enforcing privacy protections (OTP/banking code detection and filtering) over third-party relays.

---

## 1. Supported WhatsApp Delivery Modes

| Mode | Automation Level | Latency | Background Support | Best For |
|:-----|:----------------:|:-------:|:------------------:|:---------|
| **1. CallMeBot API** | 100% Automated | 1–3 sec | Yes (Screen Locked) | Quick personal setup (Free API key via WhatsApp) |
| **2. Custom Webhook** | 100% Automated | <1 sec | Yes (Screen Locked) | Self-hosted gateways (Evolution API, Baileys, n8n, Zapier) |
| **3. Interactive Draft** | Semi-Automated | Instant | No (Requires Screen Unlock + Tap) | Direct WhatsApp app without any external API or relay |

---

## 2. Privacy Guard & Sensitive Content Filter (`SensitiveFilter.kt`)

> [!CAUTION]
> **Third-Party Relay Privacy Risk (CallMeBot)**
> Forwarding SMS messages containing One-Time Passwords (OTPs), bank alerts, or 2FA codes through an unverified external relay exposes user accounts to interception.

### Sensitive Content Detector
```kotlin
object SensitiveFilter {
    private val SENSITIVE_REGEX = Regex(
        pattern = "\\b(otp|code|verification|passcode|password|bank|login|auth|2fa|security|token|pin|cvv)\\b",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val DIGIT_CODE_REGEX = Regex("\\b\\d{4,8}\\b")

    fun isSensitiveMessage(body: String): Boolean {
        val hasKeyword = SENSITIVE_REGEX.containsMatchIn(body)
        val hasDigits = DIGIT_CODE_REGEX.containsMatchIn(body)
        return hasKeyword && hasDigits
    }

    fun redactOtp(body: String): String {
        return DIGIT_CODE_REGEX.replace(body, "[REDACTED]")
    }
}
```

### Configurable Privacy Policies
Under Settings → WhatsApp (CallMeBot):
1. **Exclude Sensitive Messages (Default: ON)**:
   - If a message is identified as an OTP or financial alert, it is **skipped** for CallMeBot (logged as `SKIPPED (Privacy Guard)` in Room DB).
   - The message is still forwarded safely to your private **Telegram** bot.
2. **Redact Sensitive Code**:
   - Replaces OTP digits with `[REDACTED]` before dispatching to CallMeBot.
3. **Transparent In-App Warning**:
   - Displays a prominent alert dialog before activating CallMeBot: *"CallMeBot is a third-party relay. Never forward bank passwords or sensitive OTPs through third-party relays without encryption."*

---

## 3. CallMeBot Client with Rate-Limit Throttling (`WhatsAppSender.kt`)

CallMeBot API is rate-limited and can drop messages if fired concurrently:
- **API URL**:
  `https://api.callmebot.com/whatsapp.php?phone=<PHONE>&text=<URL_ENCODED_MSG>&apikey=<API_KEY>`
- **Throttling Queue**: Enforces a minimum interval of 1.5 seconds between outgoing requests.

---

## 4. Custom Webhook Mode

For users who run their own WhatsApp gateway (e.g. Evolution API, WPPConnect, Baileys, or n8n):
- **URL**: Configurable HTTPS endpoint.
- **Method**: `POST`
- **Headers**: Optional Authorization token / API Key.
- **Payload**:
  ```json
  {
    "simSlot": 0,
    "carrier": "T-Mobile",
    "sender": "+1234567890",
    "message": "Hello World",
    "timestamp": 1757182359
  }
  ```

---

## 5. Interactive Draft Mode (WhatsApp Intent)

When the user chooses not to use external APIs:
1. `SmsReceiver` posts a high-priority system notification: *"SMS from +123456789: Tap to send on WhatsApp"*.
2. Tapping the notification launches the WhatsApp chat:
   `Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$targetPhone&text=$encodedText"))`
3. Opens the chat with the text pre-filled into the message field.
4. The user verifies and taps **Send**.

---

## Phase 4 Verification Checklist
- [ ] CallMeBot credentials (API Key and Phone) stored securely in Keystore.
- [ ] Tapping "Test WhatsApp" validates connection and sends test text.
- [ ] Sensitive SMS containing `"Your bank OTP is 492019"` is filtered/blocked from CallMeBot when Privacy Guard is enabled.
- [ ] Non-sensitive SMS forwards cleanly to WhatsApp via CallMeBot.
- [ ] Webhook mode successfully sends JSON payload to a test HTTP endpoint.
- [ ] Intent mode shows heads-up notification and opens WhatsApp draft with pre-filled text.
