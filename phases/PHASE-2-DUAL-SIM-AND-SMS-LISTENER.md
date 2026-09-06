# Phase 2: Dual-SIM Detection & SMS Interception

## Objective
Detect active SIM cards on dual-SIM devices using Android's `SubscriptionManager`, intercept incoming SMS messages with a defensive multi-vendor SIM extractor, parse multi-part messages correctly, and persist them in a local Room database with SIM slot metadata.

---

## 1. Dual-SIM Detection Engine (`SimUtil.kt`)

Modern Android hardware implementations (Samsung, Xiaomi, Pixel, OnePlus) vary in how they report SIM slot identifiers. `SimUtil` implements a robust detection and mapping strategy:

### Querying Active Subscriptions
```kotlin
data class SimCardInfo(
    val slotIndex: Int,          // 0 for SIM 1, 1 for SIM 2
    val subscriptionId: Int,     // Android system subId
    val displayName: String,     // e.g. "SIM 1" or user custom name
    val carrierName: String,     // e.g. "T-Mobile", "Vodafone"
    val phoneNumber: String?     // If available from SIM card
)

class SimUtil(private val context: Context) {
    fun getActiveSimCards(): List<SimCardInfo> {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return emptyList()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        return subscriptionManager.activeSubscriptionInfoList?.map { subInfo ->
            SimCardInfo(
                slotIndex = subInfo.simSlotIndex,
                subscriptionId = subInfo.subscriptionId,
                displayName = subInfo.displayName?.toString() ?: "SIM ${subInfo.simSlotIndex + 1}",
                carrierName = subInfo.carrierName?.toString() ?: "Unknown Carrier",
                phoneNumber = subInfo.number
            )
        } ?: emptyList()
    }
}
```

### Defensive SIM Resolution from SMS Intent Extras
Different manufacturers use different keys in the `SMS_RECEIVED` Intent extras:
```kotlin
fun extractSimSlotFromIntent(intent: Intent, activeSims: List<SimCardInfo>): Int {
    val bundle = intent.extras ?: return 0
    
    // Check common vendor keys for subscription ID
    val subId = bundle.getInt("subscription", -1).takeIf { it != -1 }
        ?: bundle.getInt("subscriptionId", -1).takeIf { it != -1 }
        ?: bundle.getInt("android.telephony.extra.SUBSCRIPTION_INDEX", -1).takeIf { it != -1 }
        ?: bundle.getInt("simId", -1).takeIf { it != -1 }

    if (subId != null) {
        val matchedSim = activeSims.find { it.subscriptionId == subId }
        if (matchedSim != null) return matchedSim.slotIndex
    }

    // Check direct slot index keys
    val directSlot = bundle.getInt("slot", -1).takeIf { it != -1 }
        ?: bundle.getInt("slot_id", -1).takeIf { it != -1 }
        ?: bundle.getInt("simSlot", -1).takeIf { it != -1 }

    return directSlot ?: 0 // Default to SIM 1 (slot 0)
}
```

---

## 2. SMS Reception & PDU Parsing (`SmsReceiver.kt`)

Incoming SMS messages can arrive in multiple segments (multi-part SMS). `SmsReceiver`:
1. Intercepts `android.provider.Telephony.SMS_RECEIVED`.
2. Extracts PDUs and the message `format` extra (`3gpp` for GSM/LTE, `3gpp2` for CDMA).
3. Reassembles multi-part PDUs into a single coherent message text and extracts the originating address (sender) and arrival timestamp.
4. Identifies the receiving SIM slot.
5. Evaluates user SIM filter rule:
   - `ALL`: Forward all messages.
   - `SIM_1`: Drop messages from SIM 2.
   - `SIM_2`: Drop messages from SIM 1.
6. Records event into the Room database.
7. Hands off processing to `WorkManager` for network forwarding.

---

## 3. Local Audit Database (Room)

### Schema: `SmsLogEntity.kt`
```kotlin
@Entity(tableName = "sms_logs")
data class SmsLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val messageBody: String,
    val simSlotIndex: Int,          // 0 = SIM 1, 1 = SIM 2
    val simDisplayName: String,
    val carrierName: String,
    val timestamp: Long,
    val telegramStatus: String,     // PENDING, SUCCESS, FAILED, SKIPPED, DISABLED
    val whatsappStatus: String,     // PENDING, SUCCESS, FAILED, SKIPPED, DISABLED, DRAFT_CREATED
    val isSensitive: Boolean,       // True if detected as OTP / banking code
    val errorMessage: String? = null
)
```

### DAO: `SmsLogDao.kt`
- `insertLog(entity: SmsLogEntity): Long`
- `updateStatus(id: Long, telegramStatus: String, whatsappStatus: String, error: String?)`
- `getRecentLogs(limit: Int = 100): Flow<List<SmsLogEntity>>`
- `getLogsFilteredBySim(slot: Int): Flow<List<SmsLogEntity>>`
- `clearAllLogs()`

---

## 4. UI: Dual-SIM Selector Card

Displayed prominently on the `HomeScreen`:
- Live detection indicator showing both SIM cards:
  - **SIM 1**: Carrier Name (e.g. "T-Mobile") • Slot 0 • Phone Number
  - **SIM 2**: Carrier Name (e.g. "Vodafone") • Slot 1 • Phone Number
- Interactive Radio Group selection:
  - ○ Forward from Both SIMs (Recommended)
  - ○ Forward SIM 1 Only
  - ○ Forward SIM 2 Only
- Selection is immediately persisted into `AppPreferences` DataStore.

---

## Phase 2 Verification Checklist
- [ ] Device with two SIM cards (or dual-SIM emulator) lists both SIM cards with carrier names.
- [ ] Changing the SIM filter selection updates DataStore immediately.
- [ ] Injecting an SMS via emulator console (`sms send <phone> <message>`) logs message to Room DB.
- [ ] If filter is set to "SIM 1 Only", an SMS received on SIM 2 is logged with status `SKIPPED (SIM Filtered)`.
