# Phase 1: Project Scaffold, Build Tooling & Hardware-Backed Security

## Objective
Establish the foundational Android project structure with Gradle Kotlin DSL, root `Makefile`, Android 14+ permissions with `dataSync` foreground service type, hardware-backed Keystore encryption for API secrets, and a reactive Jetpack Compose navigation shell.

---

## 1. Build & Dependency Setup

### Gradle Files
- **`settings.gradle.kts`**: Configure plugin management (`google()`, `mavenCentral()`) and register `:app` module.
- **`build.gradle.kts` (root)**: AGP 8.10.x, Kotlin 2.x, Compose compiler plugin.
- **`gradle/libs.versions.toml`**: Version catalog defining:
  - `androidx-core-ktx`
  - `androidx-lifecycle-runtime-ktx`
  - `androidx-activity-compose`
  - `compose-bom` (2026.08.00)
  - `material3`, `ui`, `ui-tooling-preview`
  - `androidx-navigation-compose`
  - `androidx-datastore-preferences`
  - `androidx-security-crypto` (`1.1.0-alpha06` / `1.0.0` for `MasterKey` and `EncryptedSharedPreferences`)
  - `androidx-room-runtime`, `androidx-room-ktx`, `androidx-room-compiler`
  - `androidx-work-runtime-ktx`
  - `okhttp`
  - `kotlinx-coroutines-android`
  - `kotlinx-serialization-json`
- **`gradle.properties`**: JVM args, AndroidX, non-transitive R classes.
- **`app/build.gradle.kts`**:
  - `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`
  - Enable Compose (`buildFeatures { compose = true }`)
  - Kotlin Compose compiler plugin
  - Room compiler annotation processing (KSP)

---

## 2. Android Manifest & Permissions (Android 14+ Ready)

### `app/src/main/AndroidManifest.xml`
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- SMS Permissions -->
    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <uses-permission android:name="android.permission.READ_SMS" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />

    <!-- Network & Internet -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- Foreground Service (Android 14+ Compliant) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Boot & Battery -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <application
        android:name=".SmsForwarderApp"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.SmsForwarder">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.SmsForwarder">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Foreground Service declared with dataSync type -->
        <service
            android:name=".service.SmsForwarderService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />

        <!-- Broadcast Receivers -->
        <receiver
            android:name=".receiver.SmsReceiver"
            android:exported="true"
            android:permission="android.permission.BROADCAST_SMS">
            <intent-filter android:priority="999">
                <action android:name="android.provider.Telephony.SMS_RECEIVED" />
            </intent-filter>
        </receiver>

        <receiver
            android:name=".receiver.BootReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

---

## 3. Storage Architecture: Plaintext DataStore vs Hardware Keystore

### `SecurePreferences.kt` (Encrypted Secrets)
Sensitive API tokens and private parameters are stored using `EncryptedSharedPreferences` backed by `MasterKey` (AES256_GCM) via Android Keystore:
- `telegramBotToken: String`
- `telegramChatId: String`
- `callMeBotApiKey: String`
- `callMeBotPhoneNumber: String`
- `customWebhookUrl: String`
- `customWebhookAuthHeader: String`

### `AppPreferences.kt` (Non-Sensitive Config via DataStore)
General operational preferences stored in Jetpack Preferences DataStore:
- `isServiceEnabled: Boolean`
- `selectedSimFilter: String` (`ALL`, `SIM_1`, `SIM_2`)
- `isTelegramEnabled: Boolean`
- `isWhatsAppEnabled: Boolean`
- `whatsAppMode: String` (`CALLMEBOT`, `WEBHOOK`, `INTENT`)
- `excludeSensitiveFromCallMeBot: Boolean` (Default `true`)
- `messageTemplate: String`

---

## 4. UI Shell & Runtime Permission Flow

### Jetpack Compose Scaffold
- `MainActivity.kt`: Sets up Material 3 theme and `AppNavigation`.
- `AppNavigation.kt`: Bottom navigation bar with 3 tabs:
  1. **Home**: Service toggle, SIM card detection summary, quick status.
  2. **Settings**: Secret credentials input, SIM rules, forwarding options.
  3. **Logs**: Real-time SMS forwarding audit log.
- `PermissionHelper.kt`: Checks and requests:
  - `RECEIVE_SMS`
  - `READ_SMS`
  - `READ_PHONE_STATE`
  - `POST_NOTIFICATIONS` (Android 13+)
  Displays educational explanation banner if permission was previously denied.

---

## Phase 1 Verification Checklist
- [ ] `make check-env` validates JDK 21 and Android Studio paths.
- [ ] `make build` compiles the debug APK without warnings or errors.
- [ ] App launches and renders the 3-tab Bottom Navigation.
- [ ] Runtime permissions prompt displays on launch.
- [ ] Unit test: `SecurePreferencesTest` writes a secret token and verifies it cannot be read as plaintext from disk.
