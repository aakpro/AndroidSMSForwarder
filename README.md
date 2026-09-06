# Android SMS Forwarder (Dual-SIM & Telegram/WhatsApp)

An Android application that monitors incoming SMS messages, detects which SIM card received them on dual-SIM devices, and forwards them automatically to **Telegram** and/or **WhatsApp** according to user-configured rules.

---

## 🌟 Key Features

- **📱 Dual-SIM Support**: Detects active SIM cards via Android `SubscriptionManager` and allows filtering (Forward from Both SIMs, SIM 1 Only, or SIM 2 Only).
- **✈️ Telegram Forwarding**: 100% headless, automated 24/7 background forwarding via the official Telegram Bot API with customizable HTML templates.
- **💬 WhatsApp Forwarding**:
  - **Automated API**: Forward via CallMeBot API or custom self-hosted Webhook (Evolution API, Baileys, n8n).
  - **Interactive Draft**: Generates notifications to open WhatsApp with pre-filled text for manual send.
- **🔒 Privacy Guard (Sensitive Content Filter)**: Detects OTPs, 2FA codes, and banking alerts, allowing automatic exclusion or redaction when forwarding over unverified third-party relays like CallMeBot.
- **🛡️ Hardware-Backed Secret Storage**: All API keys, bot tokens, and phone numbers are encrypted at rest using Android Keystore (`MasterKey` with AES-256 GCM).
- **🔋 24/7 Background Reliability**: Android 14+ compliant Foreground Service (`dataSync` type) with auto-restart on boot (`RECEIVE_BOOT_COMPLETED`) and WorkManager retry policies.
- **📊 Real-time Audit Logs**: Local Room database tracking every received SMS, SIM slot, forwarding status (Success/Failure/Skipped), and error diagnostics.

---

## 🏗️ Architecture

```
[Incoming SMS] 
      │
      ▼
[SmsReceiver] ──(Multi-part PDU assembly)──> [SimUtil] (Resolve Slot 0/1)
                                                 │
                                                 ▼
                                        [SIM Filter Match?]
                                        ├── NO  ──> [Log: Skipped]
                                        └── YES ──> [ForwardWorker (WorkManager)]
                                                         │
                        ┌────────────────────────────────┴───────────────────────────────┐
                        ▼                                                                ▼
              [Telegram Sender]                                                [WhatsApp Sender]
           (Official Bot API: 24/7)                                     ┌────────────────┴────────────────┐
                        │                                               ▼                                 ▼
                        │                                      [Automated API / Webhook]       [Interactive Draft]
                        │                                    (Privacy filter for OTP/Bank)   (Pre-fill + Manual Send)
                        │                                               │                                 │
                        └───────────────────────┬───────────────────────┘                                 │
                                                ▼                                                         ▼
                                       [Room DB: Log Result]                                     [System Notification]
```

---

## 📋 Implementation Phases

Complete technical specifications and step-by-step implementation guides are located in the [`phases/`](./phases/) directory:

1. [**Phase 1: Project Scaffold, Build Tooling & Hardware-Backed Security**](./phases/PHASE-1-SCAFFOLD-AND-PERMISSIONS.md)
2. [**Phase 2: Dual-SIM Detection & SMS Interception**](./phases/PHASE-2-DUAL-SIM-AND-SMS-LISTENER.md)
3. [**Phase 3: Telegram Forwarding Engine**](./phases/PHASE-3-TELEGRAM-FORWARDING-ENGINE.md)
4. [**Phase 4: WhatsApp Forwarding Engine & Privacy Protections**](./phases/PHASE-4-WHATSAPP-FORWARDING-ENGINE.md)
5. [**Phase 5: Reliability, Logs UI & Hardening**](./phases/PHASE-5-SECURITY-POLISH-AND-MONITORING.md)

---

## 🛠️ Build Commands

A `Makefile` is included with automatic JDK detection (including macOS Android Studio JBR):

```bash
# Check Java and Android SDK environment
make check-env

# Build debug APK
make build

# Build release APK
make release

# Run JVM unit tests
make test

# Install debug APK on connected device/emulator
make install

# Launch app on device
make run

# Stream live forwarder logs
make logs

# Clean build artifacts
make clean
```
