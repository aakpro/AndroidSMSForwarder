# Phase 3: Telegram Forwarding Engine

## Objective
Implement headless, 100% automated background forwarding of intercepted SMS messages to a Telegram chat or channel via the official Telegram Bot API, with customizable HTML templates and guaranteed delivery via WorkManager.

---

## 1. Telegram Bot API Client (`TelegramSender.kt`)

Telegram Bot API provides a direct HTTP REST interface that functions 24/7 without needing user intervention or the Telegram app installed.

### API Specification
- **Endpoint**: `https://api.telegram.org/bot<BOT_TOKEN>/sendMessage`
- **Method**: `POST`
- **Content-Type**: `application/json`
- **Payload**:
  ```json
  {
    "chat_id": "<TARGET_CHAT_ID>",
    "text": "📬 <b>New SMS</b>\n...",
    "parse_mode": "HTML",
    "disable_web_page_preview": true
  }
  ```

### Implementation Architecture
```kotlin
class TelegramSender(
    private val securePreferences: SecurePreferences,
    private val okHttpClient: OkHttpClient
) {
    suspend fun sendSms(formattedMessage: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val botToken = securePreferences.telegramBotToken
        val chatId = securePreferences.telegramChatId

        if (botToken.isBlank() || chatId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Telegram credentials not configured"))
        }

        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val jsonBody = JSONObject().apply {
            put("chat_id", chatId)
            put("text", formattedMessage)
            put("parse_mode", "HTML")
            put("disable_web_page_preview", true)
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                Result.success(true)
            } else {
                val errorMsg = parseTelegramError(responseBody) ?: "HTTP ${response.code}"
                Result.failure(Exception("Telegram API Error: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(token: String, chatId: String): Result<String> {
        // Sends a synthetic test message to verify token & chat ID validity
    }
}
```

---

## 2. Template Formatting Engine (`TemplateFormatter.kt`)

Users can format how SMS messages are displayed in Telegram using placeholders.

### Supported Placeholders
| Placeholder | Description | Example |
|:------------|:------------|:--------|
| `{sim}` | SIM Slot Indicator | `SIM 1` / `SIM 2` |
| `{carrier}` | Mobile Network Carrier | `T-Mobile` / `Vodafone` |
| `{sender}` | Sender Number or Contact Name | `+123456789` |
| `{message}` | Full SMS message body | `Your code is 12345` |
| `{time}` | Time received (HH:mm:ss) | `14:32:05` |
| `{date}` | Date received (yyyy-MM-dd) | `2026-09-06` |

### Default HTML Template
```
📬 <b>New SMS Received</b>
📱 <b>SIM:</b> {sim} ({carrier})
👤 <b>From:</b> <code>{sender}</code>
🕒 <b>Time:</b> {time}

💬 <b>Message:</b>
{message}
```
*Note: Special HTML characters (`<`, `>`, `&`) within the SMS body are escaped to avoid Telegram HTML parser errors.*

---

## 3. Background Delivery with WorkManager (`ForwardWorker.kt`)

To prevent loss of messages during network dropouts:
- When an SMS arrives, `SmsReceiver` enqueues a `OneTimeWorkRequest` to `ForwardWorker`.
- **Constraints**: `NetworkType.CONNECTED`.
- **Backoff Policy**: `BackoffPolicy.EXPONENTIAL` (10s initial backoff, up to 3 automated retries).
- Updates `SmsLogDao` upon success or final failure.

---

## 4. UI: Settings & Connection Test

On `SettingsScreen`:
- Telegram Bot Token input (password/masked field).
- Target Chat ID input (with helper text on how to find Chat ID using `@userinfobot`).
- **"Test Telegram Connection"** button:
  - Shows a progress spinner.
  - Sends a test message immediately.
  - Displays a green success banner or detailed error description (e.g., "Bot token is invalid" or "Bot was not added to the chat/group").

---

## Phase 3 Verification Checklist
- [ ] Saving Telegram Bot Token & Chat ID writes them to hardware-encrypted Keystore storage.
- [ ] Tapping "Test Telegram Connection" successfully sends a message to the specified Telegram chat.
- [ ] An incoming SMS received on a target SIM triggers `ForwardWorker` and delivers to Telegram within 2 seconds.
- [ ] If phone is in Airplane Mode when SMS arrives, message queues and forwards as soon as network is restored.
- [ ] Room database updates status to `SUCCESS` with timestamp.
