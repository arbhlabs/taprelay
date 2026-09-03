# TapRelay v0.0.1: Implementation Handoff for Android Studio
**ARBH Labs — Engineering Division**  
**Target Package:** `com.arbhlabs.taprelay`  
**Target Hardware:** Google Pixel 7 (Android 14+ / API 34+)  
**Document Version:** 1.0.0-PROD-HANDOFF  

---

## 1. Project Specifications

- **Application ID:** `com.arbhlabs.taprelay`
- **Min SDK:** `30` (Android 11)
- **Target SDK:** `35` (Android 15)
- **Compile SDK:** `35`
- **JDK:** OpenJDK 17 or 21
- **Kotlin Version:** `2.0.20`
- **Build System:** Gradle Kotlin DSL (`build.gradle.kts`)
- **UI Toolkit:** Jetpack Compose with Material 3 (BOM `2024.09.00`)

---

## 2. Directory & Package Structure

```
app/src/main/java/com/arbhlabs/taprelay/
├── TapRelayApplication.kt
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── dao/
│   │   │   ├── TagDao.kt
│   │   │   ├── SmartDeviceDao.kt
│   │   │   └── ProviderConnectionDao.kt
│   │   └── entity/
│   │       ├── TagEntity.kt
│   │       ├── SmartDeviceEntity.kt
│   │       └── ProviderConnectionEntity.kt
│   ├── secure/
│   │   ├── SecureKeyStorage.kt
│   │   └── TinkAeadManager.kt
│   └── remote/
│       ├── govee/
│       │   ├── GoveeApiClient.kt
│       │   ├── GoveeModels.kt
│       │   └── GoveeLanManager.kt
│       └── google/
│           └── GoogleHomeClient.kt
├── domain/
│   ├── model/
│   │   ├── ActionType.kt
│   │   ├── SmartDevice.kt
│   │   └── TapAction.kt
│   ├── provider/
│   │   ├── SmartHomeProvider.kt
│   │   ├── GoveeCloudProvider.kt
│   │   └── GoveeLanProvider.kt
│   └── repository/
│       ├── TagRepository.kt
│       └── DeviceRepository.kt
├── nfc/
│   ├── NfcManager.kt
│   ├── NfcPayloadParser.kt
│   └── NfcWriter.kt
├── execution/
│   ├── ActionExecutor.kt
│   └── HapticsManager.kt
└── ui/
    ├── MainActivity.kt
    ├── NfcTrampolineActivity.kt
    ├── theme/
    │   ├── Color.kt
    │   ├── Theme.kt
    │   └── Type.kt
    ├── navigation/
    │   └── NavGraph.kt
    ├── components/
    │   ├── TapRelayPillOverlay.kt
    │   ├── PixelNfcVisualGuide.kt
    │   └── DeviceItemCard.kt
    └── screens/
        ├── onboarding/
        │   └── OnboardingScreen.kt
        ├── connect/
        │   └── ConnectProviderScreen.kt
        ├── home/
        │   ├── HomeScreen.kt
        │   └── HomeViewModel.kt
        └── wizard/
            ├── TagWizardScreen.kt
            └── TagWizardViewModel.kt
```

---

## 3. Dependencies (`app/build.gradle.kts`)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.arbhlabs.taprelay"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arbhlabs.taprelay"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core & Lifecycle
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Material 3 & Navigation
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Tink Security + DataStore
    implementation("com.google.crypto.tink:tink-android:1.14.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Ktor HTTP Client (Engine Android)
    val ktorVersion = "2.3.12"
    implementation("io.ktor:ktor-client-android:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
```

---

## 4. AndroidManifest.xml & Intent Filters

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.NFC" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <!-- NFC hardware feature requirement -->
    <uses-feature
        android:name="android.hardware.nfc"
        android:required="true" />

    <application
        android:name=".TapRelayApplication"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.TapRelay"
        tools:targetApi="35">

        <!-- Main UI Activity -->
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:theme="@style/Theme.TapRelay">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Lightweight Translucent Trampoline for NFC Scan Execution -->
        <activity
            android:name=".ui.NfcTrampolineActivity"
            android:exported="true"
            android:excludeFromRecents="true"
            android:noHistory="true"
            android:theme="@android:style/Theme.Translucent.NoTitleBar">

            <!-- Verified App Link Filter -->
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data
                    android:scheme="https"
                    android:host="taprelay.app"
                    android:pathPrefix="/t/" />
            </intent-filter>

            <!-- NDEF Tag Discovery Filter -->
            <intent-filter>
                <action android:name="android.nfc.action.NDEF_DISCOVERED" />
                <category android:name="android.intent.category.DEFAULT" />
                <data
                    android:scheme="https"
                    android:host="taprelay.app"
                    android:pathPrefix="/t/" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

---

## 5. Security & Encrypted Storage Engine

### `data/secure/TinkAeadManager.kt`
```kotlin
package com.arbhlabs.taprelay.data.secure

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.util.Base64

class TinkAeadManager(context: Context) {
    private val aead: Aead

    init {
        AeadConfig.register()
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle

        aead = keysetHandle.getPrimitive(Aead::class.java)
    }

    fun encrypt(plaintext: String): String {
        val encryptedBytes = aead.encrypt(plaintext.toByteArray(Charsets.UTF_8), ASSOCIATED_DATA)
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }

    fun decrypt(ciphertextBase64: String): String {
        val decodedBytes = Base64.getDecoder().decode(ciphertextBase64)
        val decryptedBytes = aead.decrypt(decodedBytes, ASSOCIATED_DATA)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    companion object {
        private const val KEYSET_NAME = "taprelay_keyset"
        private const val PREF_FILE_NAME = "taprelay_keyset_prefs"
        private const val MASTER_KEY_URI = "android-keystore://taprelay_master_key"
        private val ASSOCIATED_DATA = "TapRelayKeyBinding".toByteArray(Charsets.UTF_8)
    }
}
```

---

## 6. Local Database & Room Entities

### `data/local/entity/TagEntity.kt`
```kotlin
package com.arbhlabs.taprelay.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arbhlabs.taprelay.domain.model.ActionType

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val tagId: String,       // UUID v4 parsed from NDEF
    val friendlyName: String,           // e.g. "Desk Lamp Switch"
    val iconKey: String,                // e.g. "ic_lamp"
    val providerId: String,             // "govee"
    val deviceId: String,               // Hardware MAC address
    val deviceSku: String,              // Hardware SKU (e.g. "H6003")
    val actionType: ActionType,         // TOGGLE, TURN_ON, TURN_OFF
    val lastKnownState: Int = 1,        // 1 = ON, 0 = OFF
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long? = null
)
```

### `data/local/dao/TagDao.kt`
```kotlin
package com.arbhlabs.taprelay.data.local.dao

import androidx.room.*
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY createdAt DESC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE tagId = :tagId LIMIT 1")
    suspend fun getTagById(tagId: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity)

    @Update
    suspend fun updateTag(tag: TagEntity)

    @Query("UPDATE tags SET lastKnownState = :newState, lastTriggeredAt = :timestamp WHERE tagId = :tagId")
    suspend fun updateStateAndTimestamp(tagId: String, newState: Int, timestamp: Long)

    @Delete
    suspend fun deleteTag(tag: TagEntity)
}
```

---

## 7. Govee OpenAPI Implementation

### `data/remote/govee/GoveeApiClient.kt`
```kotlin
package com.arbhlabs.taprelay.data.remote.govee

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

class GoveeApiClient {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun getDevices(apiKey: String): Result<List<GoveeDeviceDto>> = runCatching {
        val response = client.get("https://openapi.api.govee.com/router/api/v1/user/devices") {
            header("Govee-API-Key", apiKey)
            contentType(ContentType.Application.Json)
        }
        if (response.status == HttpStatusCode.OK) {
            val body = response.body<GoveeResponse<GoveeDeviceListPayload>>()
            body.payload.devices
        } else if (response.status == HttpStatusCode.Unauthorized) {
            throw GoveeApiException.Unauthorized
        } else if (response.status == HttpStatusCode.TooManyRequests) {
            throw GoveeApiException.RateLimitExceeded
        } else {
            throw GoveeApiException.GeneralError("HTTP ${response.status.value}")
        }
    }

    suspend fun setPowerState(apiKey: String, sku: String, deviceMac: String, turnOn: Boolean): Result<Unit> = runCatching {
        val request = GoveeControlRequest(
            requestId = UUID.randomUUID().toString(),
            payload = GoveeControlPayload(
                sku = sku,
                device = deviceMac,
                capability = GoveeCapability(
                    type = "devices.capabilities.on_off",
                    instance = "powerSwitch",
                    value = if (turnOn) 1 else 0
                )
            )
        )

        val response = client.post("https://openapi.api.govee.com/router/api/v1/device/control") {
            header("Govee-API-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (response.status != HttpStatusCode.OK) {
            when (response.status) {
                HttpStatusCode.Unauthorized -> throw GoveeApiException.Unauthorized
                HttpStatusCode.TooManyRequests -> throw GoveeApiException.RateLimitExceeded
                else -> throw GoveeApiException.GeneralError("Control failed: ${response.status.value}")
            }
        }
    }
}

sealed class GoveeApiException(message: String) : Exception(message) {
    object Unauthorized : GoveeApiException("Invalid or expired API Key")
    object RateLimitExceeded : GoveeApiException("Rate limit exceeded")
    class GeneralError(msg: String) : GoveeApiException(msg)
}
```

---

## 8. NFC Engine: Reader Mode & Tag Provisioning

### `nfc/NfcManager.kt`
```kotlin
package com.arbhlabs.taprelay.nfc

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.nio.charset.StandardCharsets

class NfcManager(private val activity: Activity) {
    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    fun isNfcSupported(): Boolean = nfcAdapter != null
    fun isNfcEnabled(): Boolean = nfcAdapter?.isEnabled == true

    fun enableReader(onTagDetected: (Tag) -> Unit) {
        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        nfcAdapter?.enableReaderMode(activity, { tag ->
            activity.runOnUiThread {
                onTagDetected(tag)
            }
        }, flags, null)
    }

    fun disableReader() {
        nfcAdapter?.disableReaderMode(activity)
    }

    fun writeTapRelayTag(tag: Tag, tagUuid: String, lockTag: Boolean): Result<Unit> = runCatching {
        val uriString = "https://taprelay.app/t/$tagUuid"
        val uriRecord = NdefRecord.createUri(uriString)
        val aarRecord = NdefRecord.createApplicationRecord("com.arbhlabs.taprelay")
        val message = NdefMessage(arrayOf(uriRecord, aarRecord))

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            if (!ndef.isWritable) {
                throw IllegalStateException("NFC tag is read-only")
            }
            if (ndef.maxSize < message.byteArrayLength) {
                throw IllegalStateException("NFC tag memory is too small")
            }
            ndef.writeNdefMessage(message)
            if (lockTag && ndef.canMakeReadOnly()) {
                ndef.makeReadOnly()
            }
            ndef.close()
        } else {
            val formatable = NdefFormatable.get(tag)
                ?: throw IllegalStateException("Tag does not support NDEF formatting")
            formatable.connect()
            if (lockTag) {
                formatable.formatReadOnly(message)
            } else {
                formatable.format(message)
            }
            formatable.close()
        }
    }
}
```

---

## 9. Action Executor & Optimistic Toggle

### `execution/ActionExecutor.kt`
```kotlin
package com.arbhlabs.taprelay.execution

import android.content.Context
import com.arbhlabs.taprelay.data.local.dao.TagDao
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.provider.SmartHomeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActionExecutor(
    private val context: Context,
    private val tagDao: TagDao,
    private val providers: Map<String, SmartHomeProvider>,
    private val hapticsManager: HapticsManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun executeByTagId(tagUuid: String, onFeedback: (String, Boolean) -> Unit) {
        scope.launch {
            val tag = tagDao.getTagById(tagUuid)
            if (tag == null) {
                hapticsManager.vibrateError()
                onFeedback("Tag not set up yet", true)
                return@launch
            }

            // 1. Instant Haptic Trigger (<20ms)
            hapticsManager.vibrateClick()

            // 2. Compute Optimistic Target State
            val targetState = when (tag.actionType) {
                ActionType.TURN_ON -> 1
                ActionType.TURN_OFF -> 0
                ActionType.TOGGLE -> if (tag.lastKnownState == 1) 0 else 1
            }

            val stateLabel = if (targetState == 1) "On" else "Off"
            onFeedback("${tag.friendlyName} • $stateLabel", false)

            // 3. Update Local Cache Optimistically
            tagDao.updateStateAndTimestamp(tag.tagId, targetState, System.currentTimeMillis())

            // 4. Asynchronously Dispatch to Provider
            val provider = providers[tag.providerId]
            if (provider == null) {
                revertState(tag, "Service unavailable", onFeedback)
                return@launch
            }

            val result = provider.executeAction(
                deviceId = tag.deviceId,
                sku = tag.deviceSku,
                action = tag.actionType,
                targetState = targetState
            )

            result.onFailure { error ->
                revertState(tag, "Could not reach ${tag.friendlyName}", onFeedback)
            }
        }
    }

    private suspend fun revertState(
        tag: com.arbhlabs.taprelay.data.local.entity.TagEntity,
        errorMessage: String,
        onFeedback: (String, Boolean) -> Unit
    ) {
        tagDao.updateStateAndTimestamp(tag.tagId, tag.lastKnownState, System.currentTimeMillis())
        hapticsManager.vibrateError()
        onFeedback(errorMessage, true)
    }
}
```

---

## 10. NfcTrampolineActivity (Instant Scan Handler)

### `ui/NfcTrampolineActivity.kt`
```kotlin
package com.arbhlabs.taprelay.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.nfc.NfcPayloadParser

class NfcTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data
        val tagUuid = NfcPayloadParser.parseUuidFromUri(uri)

        if (tagUuid != null) {
            val app = application as TapRelayApplication
            app.actionExecutor.executeByTagId(tagUuid) { message, isError ->
                runOnUiThread {
                    Toast.makeText(this@NfcTrampolineActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        // Finish immediately to remain completely invisible and lightweight
        finish()
    }
}
```

---

## 11. Pixel-Grade Haptics Implementation

### `execution/HapticsManager.kt`
```kotlin
package com.arbhlabs.taprelay.execution

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticsManager(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun vibrateClick() {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    fun vibrateSuccess() {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    }

    fun vibrateError() {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
    }

    fun vibrateTick() {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }
}
```

---

## 12. Build & Run Checklist for Android Studio

1. **Clone & Open:**
   - Open project root in **Android Studio Ladybug (2024.2+)** or newer.
   - Ensure Gradle JDK is configured to **Java 17 or Java 21**.
2. **Sync Project with Gradle Files:**
   - Verify all dependencies resolve cleanly via Google Maven and Maven Central.
3. **Physical Device Setup (Google Pixel 7):**
   - Connect Pixel 7 via USB; enable Developer Options & USB Debugging.
   - Ensure NFC is enabled in phone settings (`Settings -> Connected devices -> Connection preferences -> NFC`).
4. **App Launch & Onboarding:**
   - Run `app` configuration.
   - On the Onboarding screen, tap **"Get Started"**.
   - Select **Govee**. Enter your personal Govee API Key (obtained from Govee Home App -> Settings -> Apply for API Key).
5. **Tag Programming:**
   - Tap **"+ Add Tag"**.
   - Touch an NTAG213 NFC sticker against the top-center back of the Pixel 7.
   - Select "Desk Lamp" (or H6003 test bulb), choose "Toggle", tap "Save Tag".
6. **Physical Test:**
   - Exit to the Android Home Screen.
   - Tap the programmed sticker to the back of the phone.
   - Verify immediate haptic click and toast confirmation: `Desk Lamp • Toggled`.
   - Verify physical Govee bulb changes state within ~500ms.

---
*Ready for Android Studio Implementation.*
