package com.arbhlabs.taprelay.monetization

import android.content.Context
import com.arbhlabs.taprelay.data.secure.ProLicense
import com.arbhlabs.taprelay.data.secure.SecureKeyStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

enum class TapRelayProFeature {
    MULTI_DEVICE_ROUTINES,
    TIME_OF_DAY_CONDITIONS,
    TAP_DIAGNOSTICS_REPLAY,
    TAG_STORE_DISCOUNT
}

@Serializable
data class LicenseActivationRequest(
    val license_key: String,
    val app_id: String,
    val installation_id: String,
    val app_version: String = "0.1.1"
)

@Serializable
data class LicenseActivationResponse(
    val success: Boolean = false,
    val tier: String = "free",
    val status: String = "INACTIVE",
    val product: String = "",
    val plan: String = "",
    val license_id: String = "",
    val signature: String = "",
    val expires_at: Long = 0L,
    val message: String? = null
)

class EntitlementRepository(
    private val context: Context? = null,
    private val secureStorage: SecureKeyStorage? = null,
    engine: HttpClientEngine? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = (if (engine != null) HttpClient(engine) else HttpClient(Android)).config {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 6_000
        }
    }

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _proLicense = MutableStateFlow<ProLicense?>(null)
    val proLicense: StateFlow<ProLicense?> = _proLicense.asStateFlow()

    init {
        scope.launch {
            secureStorage?.getProLicense()?.collect { lic ->
                _proLicense.value = lic
                _isPro.value = lic != null && (lic.expiresAt == 0L || lic.expiresAt > System.currentTimeMillis())
            }
        }
    }

    fun canAccess(feature: TapRelayProFeature): Boolean {
        return _isPro.value
    }

    fun isTrialActive(): Boolean {
        val lic = _proLicense.value ?: return false
        return lic.tier == "trial" && (lic.expiresAt == 0L || lic.expiresAt > System.currentTimeMillis())
    }

    fun getTrialDaysRemaining(): Int {
        val lic = _proLicense.value ?: return 0
        if (lic.tier != "trial") return 0
        val remainingMs = lic.expiresAt - System.currentTimeMillis()
        if (remainingMs <= 0L) return 0
        val oneDayMs = 1000L * 60L * 60L * 24L
        return ((remainingMs + oneDayMs - 1L) / oneDayMs).toInt()
    }

    /**
     * Activates a 1-tap 7-day free trial on device.
     * Requires no credit card or account creation.
     */
    suspend fun startFreeTrial(): Result<String> {
        if (_isPro.value) {
            return Result.success("TapRelay Pro is already active.")
        }
        val current = _proLicense.value
        if (current != null && current.tier == "trial" && current.expiresAt <= System.currentTimeMillis()) {
            return Result.failure(Exception("Your 7-day free trial has expired. Activate a license to continue."))
        }
        val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
        val trialLic = ProLicense(
            licenseKey = "TRIAL-7DAY-ACTIVE",
            tier = "trial",
            expiresAt = System.currentTimeMillis() + sevenDaysMs,
            signature = "local_trial_verified"
        )
        secureStorage?.saveProLicense(trialLic)
        _proLicense.value = trialLic
        _isPro.value = true
        return Result.success("7-day free trial activated! Full Pro capabilities unlocked.")
    }

    /**
     * Activates a license key or admin passphrase against the ARBH Labs API.
     * Retains cached offline state if network is unavailable.
     */
    suspend fun activateLicense(licenseKey: String): Result<String> {
        val trimmed = licenseKey.trim().uppercase()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter a license key."))
        }

        // Offline Administrator bypass keys (including Aaron's Master Admin PIN)
        val normalized = trimmed.replace("-", "").replace(" ", "")
        if (trimmed == "ADMIN-PRO-UNLOCKED" || trimmed == "ARBH-DEV-ACCESS" ||
            trimmed == "96275562199744435376961556288749" || normalized == "96275562199744435376961556288749") {
            val adminLic = ProLicense(
                licenseKey = "96275562199744435376961556288749",
                tier = "admin",
                expiresAt = 0L,
                signature = "local_admin_verified"
            )
            secureStorage?.saveProLicense(adminLic)
            _proLicense.value = adminLic
            _isPro.value = true
            return Result.success("Administrator Pro access granted.")
        }

        return try {
            val pkg = context?.packageName ?: "com.arbhlabs.taprelay"
            val installationId = UUID.nameUUIDFromBytes(pkg.toByteArray()).toString()
            val req = LicenseActivationRequest(
                license_key = trimmed,
                app_id = "com.arbhlabs.taprelay",
                installation_id = installationId
            )
            val res = client.post("https://arbhlabs.com/api/license/activate") {
                contentType(ContentType.Application.Json)
                setBody(req)
            }
            val body = res.body<LicenseActivationResponse>()
            if (body.success && (body.status == "ACTIVE" || body.tier == "admin" || body.tier == "pro")) {
                val lic = ProLicense(
                    licenseKey = trimmed,
                    tier = body.tier,
                    expiresAt = body.expires_at,
                    signature = body.signature
                )
                secureStorage?.saveProLicense(lic)
                _proLicense.value = lic
                _isPro.value = true
                Result.success("Tap Relay Pro activated successfully.")
            } else {
                Result.failure(Exception(body.message ?: "Invalid or expired license key."))
            }
        } catch (e: Exception) {
            if (_isPro.value) {
                Result.success("Pro active (cached offline).")
            } else {
                Result.failure(Exception("Unable to activate license: Check connection or verify key."))
            }
        }
    }

    suspend fun deactivate() {
        secureStorage?.clearProLicense()
        _proLicense.value = null
        _isPro.value = false
    }
}
