package com.arbhlabs.taprelay.external

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.arbhlabs.taprelay.TapRelayApplication

/**
 * LastDose's in-app AOD is the focused controller window. This intentionally tiny provider lets
 * only that package forward an already-normalized press to TapRelay; it exposes no data, mappings
 * or general action API. ContentProvider caller identity is authoritative for a synchronous call.
 */
class LastDoseControllerProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (callingPackage != LASTDOSE_PACKAGE || method != METHOD_FORWARD) throw SecurityException("Not permitted")
        val key = extras?.getString(KEY_INPUT).orEmpty()
        val descriptor = extras?.getString(KEY_DESCRIPTOR).orEmpty().ifBlank { "*" }
        val name = extras?.getString(KEY_NAME).orEmpty().ifBlank { "Gamepad" }
        val deviceId = extras?.getInt(KEY_DEVICE_ID, -1) ?: -1
        val accepted = (context?.applicationContext as? TapRelayApplication)?.services?.controllerManager
            ?.handleForwardedInput(key, descriptor, name, deviceId) == true
        return Bundle().apply { putBoolean(KEY_ACCEPTED, accepted) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.arbhlabs.taprelay.aod_controller"
        const val METHOD_FORWARD = "forward_controller_press"
        const val KEY_INPUT = "input"
        const val KEY_DESCRIPTOR = "descriptor"
        const val KEY_NAME = "name"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_ACCEPTED = "accepted"
        private const val LASTDOSE_PACKAGE = "com.lastdose.app"
    }
}
