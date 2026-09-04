package com.arbhlabs.taprelay.execution.lastdose

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log

/** One LastDose log the owner can point an item at. */
data class LastDoseItem(
    val id: Long,
    val name: String,
    val unit: String,
    val defaultAmount: String
)

/** What came back from a log attempt. [LastDoseResult.Logged] is the only success. */
sealed interface LastDoseResult {
    /** LastDose created the event and read it back. */
    data class Logged(val eventId: Long, val itemName: String, val amount: String, val unit: String) : LastDoseResult

    /** The same request arrived twice; the first one is the log that exists. */
    data class Duplicate(val eventId: Long, val itemName: String) : LastDoseResult

    /** Something went wrong, described the way the owner should hear it. */
    data class Failed(val message: String, val reason: Reason) : LastDoseResult

    enum class Reason { NOT_INSTALLED, TOO_OLD, TARGET_MISSING, REJECTED, UNAVAILABLE }
}

/**
 * TapRelay's half of the LastDose logging contract.
 *
 * A `ContentResolver.call` into LastDose's `call()`-only provider: it runs in LastDose's process
 * without any activity being started, so nothing comes to the foreground and nothing is navigated.
 * Blocking by nature - callers must be on a background dispatcher.
 *
 * The contract mirrors `com.lastdose.app.external.ExternalActionContract`; the two files are kept
 * in step by hand and the handshake below refuses a LastDose too old to know about it.
 */
class LastDoseClient(private val context: Context) {

    private companion object {
        const val TAG = "TapRelayLastDose"
        const val PACKAGE = "com.lastdose.app"
        const val AUTHORITY = "com.lastdose.app.external"
        const val CONTRACT_VERSION = 1

        const val METHOD_CONTRACT = "contract"
        const val METHOD_ITEMS = "items"
        const val METHOD_LOG = "log"

        const val KEY_ITEM_ID = "itemId"
        const val KEY_AMOUNT = "amount"
        const val KEY_UNIT = "unit"
        const val KEY_REQUEST_ID = "requestId"
        const val KEY_STATUS = "status"
        const val KEY_EVENT_ID = "eventId"
        const val KEY_NAME = "name"
        const val KEY_DEFAULT_AMOUNT = "defaultAmount"
        const val KEY_ITEMS = "items"
        const val KEY_CONTRACT_VERSION = "contractVersion"

        const val STATUS_LOGGED = "LOGGED"
        const val STATUS_DUPLICATE = "DUPLICATE_IGNORED"
        const val STATUS_TARGET_MISSING = "TARGET_MISSING"
    }

    private val uri: Uri = Uri.parse("content://$AUTHORITY")

    /** True when a LastDose that understands this contract is installed. */
    fun isAvailable(): Boolean = contractVersion() != null

    /** LastDose's contract version, or null when it is missing, too old, or unreachable. */
    fun contractVersion(): Int? = call(METHOD_CONTRACT, null)?.let { result ->
        val version = result.getInt(KEY_CONTRACT_VERSION, 0)
        if (version <= 0) null else version
    }

    /** Every log the owner has in LastDose, for the picker. Empty when LastDose is unreachable. */
    fun items(): List<LastDoseItem> {
        val result = call(METHOD_ITEMS, null) ?: return emptyList()
        val raw = if (android.os.Build.VERSION.SDK_INT >= 33) {
            result.getParcelableArrayList(KEY_ITEMS, Bundle::class.java)
        } else {
            @Suppress("DEPRECATION") result.getParcelableArrayList<Bundle>(KEY_ITEMS)
        } ?: return emptyList()
        return raw.map {
            LastDoseItem(
                id = it.getLong(KEY_ITEM_ID),
                name = it.getString(KEY_NAME).orEmpty(),
                unit = it.getString(KEY_UNIT).orEmpty(),
                defaultAmount = it.getString(KEY_DEFAULT_AMOUNT).orEmpty()
            )
        }.filter { it.id > 0L }
    }

    /**
     * Creates exactly one log. [requestId] makes a repeated delivery of the same intent a no-op
     * on LastDose's side, so a duplicated trigger can never double-log.
     */
    fun log(itemId: Long, amount: String?, unit: String?, requestId: String): LastDoseResult {
        if (itemId <= 0L) {
            return LastDoseResult.Failed("That LastDose log is no longer set up.", LastDoseResult.Reason.TARGET_MISSING)
        }
        if (!isInstalled()) {
            return LastDoseResult.Failed("LastDose isn't installed.", LastDoseResult.Reason.NOT_INSTALLED)
        }
        val extras = Bundle().apply {
            putLong(KEY_ITEM_ID, itemId)
            amount?.let { putString(KEY_AMOUNT, it) }
            unit?.let { putString(KEY_UNIT, it) }
            putString(KEY_REQUEST_ID, requestId)
        }
        val result = call(METHOD_LOG, extras)
            ?: return LastDoseResult.Failed(
                "Update LastDose to log from TapRelay.",
                LastDoseResult.Reason.TOO_OLD
            )

        val name = result.getString(KEY_NAME).orEmpty()
        return when (result.getString(KEY_STATUS)) {
            STATUS_LOGGED -> LastDoseResult.Logged(
                eventId = result.getLong(KEY_EVENT_ID),
                itemName = name,
                amount = result.getString(KEY_AMOUNT).orEmpty(),
                unit = result.getString(KEY_UNIT).orEmpty()
            )
            STATUS_DUPLICATE -> LastDoseResult.Duplicate(result.getLong(KEY_EVENT_ID), name)
            STATUS_TARGET_MISSING -> LastDoseResult.Failed(
                "That LastDose log no longer exists.",
                LastDoseResult.Reason.TARGET_MISSING
            )
            // LastDose looked at it and did not write a row. Never dress this up as success.
            else -> LastDoseResult.Failed("LastDose couldn't save that log.", LastDoseResult.Reason.REJECTED)
        }
    }

    fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    private fun call(method: String, extras: Bundle?): Bundle? = try {
        resolver().call(uri, method, null, extras)
    } catch (e: SecurityException) {
        // The provider refused this build's signature: a debug TapRelay talking to a release
        // LastDose. Nothing the owner can fix in the moment, so it reads as unavailable.
        Log.w(TAG, "$method refused by LastDose", e)
        null
    } catch (e: IllegalArgumentException) {
        // No such provider: LastDose is missing or predates the contract.
        Log.i(TAG, "$method: LastDose provider not present")
        null
    } catch (e: Exception) {
        Log.w(TAG, "$method failed", e)
        null
    }

    private fun resolver(): ContentResolver = context.contentResolver
}
