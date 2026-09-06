# Android SMS Forwarder — Implementation Phases

This folder contains the complete, detailed specifications for implementing the **Android SMS Forwarder** app in 5 sequential, testable phases.

---

## Phase Overview

| Phase | Title | Focus Area | Deliverable |
|:-----:|:------|:-----------|:------------|
| **[Phase 1](./PHASE-1-SCAFFOLD-AND-PERMISSIONS.md)** | **Scaffold, Tooling & Security** | Gradle build, `Makefile`, Android 14+ permissions, Keystore encryption, UI shell | Compilable APK with bottom navigation & encrypted preferences |
| **[Phase 2](./PHASE-2-DUAL-SIM-AND-SMS-LISTENER.md)** | **Dual-SIM & SMS Interception** | `SubscriptionManager`, BroadcastReceiver, Room DB, SIM card filtering | Receiving & identifying SMS per SIM slot (SIM 1 vs SIM 2) |
| **[Phase 3](./PHASE-3-TELEGRAM-FORWARDING-ENGINE.md)** | **Telegram Bot Forwarder** | OkHttp Telegram Bot API, WorkManager retries, template engine | 100% automated background forwarding to Telegram |
| **[Phase 4](./PHASE-4-WHATSAPP-FORWARDING-ENGINE.md)** | **WhatsApp Forwarding & Privacy** | CallMeBot, custom Webhook, Interactive Draft, Sensitive Content Filter | Forwarding to WhatsApp with OTP/Bank data protection |
| **[Phase 5](./PHASE-5-SECURITY-POLISH-AND-MONITORING.md)** | **Reliability & Production Polish** | Foreground service (`dataSync`), Boot auto-start, live logs, battery optimization guide | Production-ready, 24/7 background-reliable SMS forwarder |

---

## Architecture Flow

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

## Quick Start Commands

```bash
# Verify Java (auto-detected from Android Studio) and Android SDK
make check-env

# Build debug APK
make build

# Run unit tests
make test

# Install on connected device / emulator
make install

# Launch app
make run

# Stream live forwarder logs
make logs
```
