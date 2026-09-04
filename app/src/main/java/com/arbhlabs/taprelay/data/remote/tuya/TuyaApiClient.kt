package com.arbhlabs.taprelay.data.remote.tuya

import android.util.Log
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.UnknownHostException
import java.util.UUID

class TuyaApiClient(engine: HttpClientEngine? = null) {

    private companion object {
        const val TAG = "TapRelayTuya"
        const val DEVICE_PAGE_SIZE = 20
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = (if (engine != null) HttpClient(engine) else HttpClient(Android)).config {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 12_000
            connectTimeoutMillis = 8_000
            socketTimeoutMillis = 12_000
        }
    }

    private var cachedToken: String? = null
    private var cachedUid: String? = null
    private var tokenExpiryEpoch: Long = 0L

    fun baseUrlForRegion(region: String): String = when (region.trim().lowercase()) {
        "eu", "europe" -> "https://openapi.tuyaeu.com"
        "cn", "china" -> "https://openapi.tuyacn.com"
        "in", "india" -> "https://openapi.tuyain.com"
        "ueaz", "us-east" -> "https://openapi-ueaz.tuyaus.com"
        else -> "https://openapi.tuyaus.com"
    }

    suspend fun getAccessToken(accessId: String, secret: String, region: String): Pair<String, String> = wrap {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiryEpoch - 60_000) {
            return@wrap Pair(cachedToken!!, cachedUid ?: "")
        }

        val base = baseUrlForRegion(region)
        val path = "/v1.0/token?grant_type=1"
        val t = now.toString()
        val nonce = UUID.randomUUID().toString()
        val sign = TuyaSigner.sign(
            clientId = accessId,
            secret = secret,
            t = t,
            nonce = nonce,
            httpMethod = "GET",
            url = path
        )

        val res = client.get("$base$path") {
            header("client_id", accessId)
            header("sign", sign)
            header("t", t)
            header("nonce", nonce)
            header("sign_method", "HMAC-SHA256")
            contentType(ContentType.Application.Json)
        }
        check(res)

        val body = res.body<TuyaResponse<TuyaTokenResult>>()
        if (!body.success || body.result == null) {
            throw TapException(TapError.AUTH_TUYA)
        }

        cachedToken = body.result.accessToken
        cachedUid = body.result.uid
        tokenExpiryEpoch = now + (body.result.expireTime * 1000)
        Pair(cachedToken!!, cachedUid ?: "")
    }

    /**
     * Performs a signed GET and returns the parsed body, logging the outcome.
     * Only response bodies are logged; credentials and tokens never are.
     */
    private suspend fun signedGet(
        accessId: String,
        secret: String,
        region: String,
        token: String,
        path: String
    ): JsonObject? {
        val base = baseUrlForRegion(region)
        val t = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString()
        val sign = TuyaSigner.sign(
            clientId = accessId,
            secret = secret,
            t = t,
            nonce = nonce,
            httpMethod = "GET",
            url = path,
            accessToken = token
        )
        val res = client.get("$base$path") {
            header("client_id", accessId)
            header("access_token", token)
            header("sign", sign)
            header("t", t)
            header("nonce", nonce)
            header("sign_method", "HMAC-SHA256")
            contentType(ContentType.Application.Json)
        }
        val rawText = runCatching { res.bodyAsText() }.getOrDefault("")
        if (res.status != HttpStatusCode.OK) {
            Log.w(TAG, "$path -> HTTP ${res.status.value}")
            return null
        }
        val root = runCatching { json.parseToJsonElement(rawText).jsonObject }.getOrNull()
        if (root == null) {
            Log.w(TAG, "$path -> unparseable body")
            return null
        }
        if (root["success"]?.jsonPrimitive?.booleanOrNull != true) {
            val code = root["code"]?.jsonPrimitive?.contentOrNull ?: "?"
            val msg = root["msg"]?.jsonPrimitive?.contentOrNull ?: "?"
            Log.w(TAG, "$path -> code=$code msg=$msg")
            return null
        }
        return root
    }

    private fun devicesFromResult(root: JsonObject): List<TuyaDeviceDto> {
        val resultEl = root["result"] ?: return emptyList()
        val arr: JsonArray = when (resultEl) {
            is JsonArray -> resultEl
            is JsonObject -> (resultEl["list"] ?: resultEl["devices"]) as? JsonArray ?: return emptyList()
            else -> return emptyList()
        }
        val first = arr.firstOrNull() as? JsonObject
        val isProjectShape = first != null && (first.containsKey("customName") || first.containsKey("isOnline"))
        return if (isProjectShape) {
            runCatching { json.decodeFromJsonElement<List<TuyaProjectDeviceDto>>(arr) }
                .getOrDefault(emptyList())
                .filter { it.id.isNotBlank() }
                .map { it.toLegacyDto() }
        } else {
            runCatching { json.decodeFromJsonElement<List<TuyaDeviceDto>>(arr) }.getOrDefault(emptyList())
        }
    }

    /** Candidate app-account UIDs for the legacy Smart Home endpoints. */
    private fun resolveUids(preferred: String, tokenUid: String): List<String> {
        val uids = LinkedHashSet<String>()
        if (preferred.isNotBlank()) uids += preferred
        if (tokenUid.isNotBlank()) uids += tokenUid
        return uids.toList()
    }

    /** Fills in the live status list for devices discovered through endpoints that omit it. */
    private suspend fun enrichStatus(
        accessId: String,
        secret: String,
        region: String,
        token: String,
        devices: List<TuyaDeviceDto>
    ): List<TuyaDeviceDto> = devices.map { dev ->
        if (dev.status.isNotEmpty() || dev.id.isBlank()) return@map dev
        val root = signedGet(accessId, secret, region, token, "/v1.0/devices/${dev.id}/status")
        val arr = root?.get("result") as? JsonArray ?: return@map dev
        val status = runCatching { json.decodeFromJsonElement<List<TuyaDeviceStatusDto>>(arr) }.getOrDefault(emptyList())
        dev.copy(status = status)
    }

    suspend fun getDevices(accessId: String, secret: String, region: String, uid: String): List<TuyaDeviceDto> = wrap {
        val (token, tokenUid) = getAccessToken(accessId, secret, region)
        val uids = resolveUids(uid.trim(), tokenUid)
        val found = LinkedHashMap<String, TuyaDeviceDto>()

        val paths = ArrayList<String>()
        // Current IoT Core projects answer "Query Devices in Project" here.
        paths += "/v2.0/cloud/thing/device?page_size=$DEVICE_PAGE_SIZE"
        // Legacy Smart Home projects are keyed by linked app-account UID.
        for (u in uids) paths += "/v1.0/users/$u/devices"
        paths += "/v1.0/devices?page_no=0&page_size=$DEVICE_PAGE_SIZE"

        for (p in paths) {
            if (found.isNotEmpty()) break
            val root = signedGet(accessId, secret, region, token, p) ?: continue
            val devs = devicesFromResult(root)
            devs.forEach { if (it.id.isNotBlank()) found[it.id] = it }
            if (devs.isNotEmpty()) Log.i(TAG, "Tuya discovery satisfied by $p")
        }

        // Finally, walk the user's homes.
        if (found.isEmpty()) {
            for (u in uids) {
                val homesRoot = signedGet(accessId, secret, region, token, "/v1.0/users/$u/homes") ?: continue
                val homes = homesRoot["result"] as? JsonArray ?: continue
                for (h in homes) {
                    val hid = (h as? JsonObject)?.get("home_id")?.jsonPrimitive?.contentOrNull ?: continue
                    val hr = signedGet(accessId, secret, region, token, "/v1.0/homes/$hid/devices") ?: continue
                    devicesFromResult(hr).forEach { if (it.id.isNotBlank()) found[it.id] = it }
                }
            }
        }

        Log.i(TAG, "Tuya discovery found ${found.size} device(s)")
        enrichStatus(accessId, secret, region, token, found.values.toList())
    }

    suspend fun getHomes(accessId: String, secret: String, region: String, uid: String): List<TuyaHomeDto> = wrap {
        val (token, resolvedUid) = getAccessToken(accessId, secret, region)
        val targetUid = uid.ifBlank { resolvedUid }
        if (targetUid.isBlank()) return@wrap emptyList()

        val base = baseUrlForRegion(region)
        val path = "/v1.0/users/$targetUid/homes"
        val t = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString()
        val sign = TuyaSigner.sign(
            clientId = accessId,
            secret = secret,
            t = t,
            nonce = nonce,
            httpMethod = "GET",
            url = path,
            accessToken = token
        )

        val res = client.get("$base$path") {
            header("client_id", accessId)
            header("access_token", token)
            header("sign", sign)
            header("t", t)
            header("nonce", nonce)
            header("sign_method", "HMAC-SHA256")
            contentType(ContentType.Application.Json)
        }
        if (res.status != HttpStatusCode.OK) return@wrap emptyList()

        val body = runCatching { res.body<TuyaResponse<List<TuyaHomeDto>>>() }.getOrNull()
        body?.result ?: emptyList()
    }

    /** Live online flag, or null when the project cannot answer the detail endpoint. */
    suspend fun getDeviceOnline(accessId: String, secret: String, region: String, deviceId: String): Boolean? = wrap {
        val (token, _) = getAccessToken(accessId, secret, region)
        val root = signedGet(accessId, secret, region, token, "/v1.0/devices/$deviceId")
            ?: return@wrap null
        val result = root["result"] as? JsonObject ?: return@wrap null
        (result["online"] ?: result["isOnline"])?.jsonPrimitive?.booleanOrNull
    }

    /** Authoritative live data points. Throws when the device cannot be reached. */
    suspend fun getDeviceStatus(accessId: String, secret: String, region: String, deviceId: String): List<TuyaDeviceStatusDto> = wrap {
        val (token, _) = getAccessToken(accessId, secret, region)
        val root = signedGet(accessId, secret, region, token, "/v1.0/devices/$deviceId/status")
            ?: throw TapException(TapError.DEVICE_OFFLINE)
        val arr = root["result"] as? JsonArray ?: return@wrap emptyList()
        runCatching { json.decodeFromJsonElement<List<TuyaDeviceStatusDto>>(arr) }.getOrDefault(emptyList())
    }

    suspend fun sendPowerCommand(
        accessId: String,
        secret: String,
        region: String,
        deviceId: String,
        dpCode: String,
        turnOn: Boolean
    ) = sendCommands(accessId, secret, region, deviceId, listOf(TuyaCommandItem(dpCode, JsonPrimitive(turnOn))))

    /** Sends one or more data points in a single request, which is what the API expects. */
    suspend fun sendCommands(
        accessId: String,
        secret: String,
        region: String,
        deviceId: String,
        commands: List<TuyaCommandItem>
    ) = wrap {
        val (token, _) = getAccessToken(accessId, secret, region)
        val base = baseUrlForRegion(region)
        val path = "/v1.0/devices/$deviceId/commands"

        val payload = TuyaCommandPayload(commands = commands)
        val bodyStr = json.encodeToString(payload)

        val t = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString()
        val sign = TuyaSigner.sign(
            clientId = accessId,
            secret = secret,
            t = t,
            nonce = nonce,
            httpMethod = "POST",
            url = path,
            body = bodyStr,
            accessToken = token
        )

        val res = client.post("$base$path") {
            header("client_id", accessId)
            header("access_token", token)
            header("sign", sign)
            header("t", t)
            header("nonce", nonce)
            header("sign_method", "HMAC-SHA256")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        check(res)

        val rawText = res.bodyAsText()
        val root = runCatching { json.parseToJsonElement(rawText).jsonObject }.getOrNull()
        val success = root?.get("success")?.jsonPrimitive?.booleanOrNull ?: false
        val code = root?.get("code")?.jsonPrimitive?.intOrNull
        val msg = root?.get("msg")?.jsonPrimitive?.contentOrNull ?: ""

        if (!success && code != 200 && code != 0) {
            if (code == 10203 || code == 2001 || msg.contains("offline", ignoreCase = true)) {
                throw TapException(TapError.DEVICE_OFFLINE)
            }
            throw TapException(TapError.SERVER)
        }
    }

    suspend fun getScenes(accessId: String, secret: String, region: String, uid: String): List<TuyaSceneDto> = wrap {
        val (token, resolvedUid) = getAccessToken(accessId, secret, region)
        val targetUid = uid.ifBlank { resolvedUid }
        if (targetUid.isBlank()) return@wrap emptyList()

        val base = baseUrlForRegion(region)
        val path = "/v1.0/iot-01/voice/users/$targetUid/scenes"

        val t = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString()
        val sign = TuyaSigner.sign(
            clientId = accessId,
            secret = secret,
            t = t,
            nonce = nonce,
            httpMethod = "GET",
            url = path,
            accessToken = token
        )

        val res = client.get("$base$path") {
            header("client_id", accessId)
            header("access_token", token)
            header("sign", sign)
            header("t", t)
            header("nonce", nonce)
            header("sign_method", "HMAC-SHA256")
            contentType(ContentType.Application.Json)
        }
        if (res.status == HttpStatusCode.NotFound || res.status.value >= 400) {
            return@wrap emptyList()
        }

        val body = runCatching { res.body<TuyaResponse<List<TuyaSceneDto>>>() }.getOrNull()
        body?.result ?: emptyList()
    }

    suspend fun triggerScene(accessId: String, secret: String, region: String, sceneId: String) = wrap {
        val (token, _) = getAccessToken(accessId, secret, region)
        val base = baseUrlForRegion(region)
        val path = "/v1.0/iot-01/voice/scenes/$sceneId/trigger"

        val t = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString()
        val sign = TuyaSigner.sign(
            clientId = accessId,
            secret = secret,
            t = t,
            nonce = nonce,
            httpMethod = "POST",
            url = path,
            accessToken = token
        )

        val res = client.post("$base$path") {
            header("client_id", accessId)
            header("access_token", token)
            header("sign", sign)
            header("t", t)
            header("nonce", nonce)
            header("sign_method", "HMAC-SHA256")
            contentType(ContentType.Application.Json)
        }
        check(res)

        val body = res.body<TuyaResponse<Boolean>>()
        if (!body.success && body.code != 200 && body.code != 0) {
            throw TapException(TapError.SERVER)
        }
    }

    private suspend fun check(res: HttpResponse) {
        when (res.status) {
            HttpStatusCode.OK -> return
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> throw TapException(TapError.AUTH_TUYA)
            HttpStatusCode.TooManyRequests -> throw TapException(TapError.RATE_LIMIT)
            else -> {
                runCatching { res.bodyAsText() }
                if (res.status.value >= 500) throw TapException(TapError.SERVER)
                throw TapException(TapError.UNKNOWN)
            }
        }
    }

    private suspend fun <T> wrap(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: TapException) {
            throw e
        } catch (e: UnknownHostException) {
            throw TapException(TapError.OFFLINE)
        } catch (e: IOException) {
            throw TapException(TapError.OFFLINE)
        } catch (e: Exception) {
            throw TapException(TapError.UNKNOWN)
        }
    }
}
