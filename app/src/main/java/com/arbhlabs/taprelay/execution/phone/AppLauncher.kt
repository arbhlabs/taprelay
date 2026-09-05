package com.arbhlabs.taprelay.execution.phone

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.arbhlabs.taprelay.domain.model.LaunchKind

/** An app on this phone that can actually be opened, as shown in the item picker. */
data class LaunchableApp(
    val packageName: String,
    val label: String
)

/**
 * Opens an app or a link.
 *
 * Only ever a launch intent or an `ACTION_VIEW` on an http(s) address - never an arbitrary intent
 * assembled from stored text. A TapRelay item is something the owner can hand to somebody else on
 * a sticker, so it must not be able to describe an arbitrary thing for the phone to do.
 */
class AppLauncher(private val context: Context) {

    /** Apps with a launcher entry, alphabetical. Reads the package manager, so never on the UI thread. */
    fun launchableApps(): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                LaunchableApp(pkg, info.loadLabel(pm)?.toString().orEmpty().ifBlank { pkg })
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun labelFor(packageName: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()

    fun isInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getLaunchIntentForPackage(packageName) != null
    }.getOrDefault(false)

    /**
     * Opens [target], which is a package name when [kind] is [LaunchKind.APP] and an http(s)
     * address when it is [LaunchKind.LINK]. Returns a plain-language failure, never an exception.
     */
    fun launch(kind: String, target: String): LaunchResult {
        val intent = when (kind) {
            LaunchKind.LINK -> {
                val url = target.trim()
                if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) {
                    return LaunchResult.Failed("That link doesn't look right.")
                }
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }
            else -> context.packageManager.getLaunchIntentForPackage(target)
                ?: return LaunchResult.Failed("That app isn't installed any more.")
        }
        // A tap can come from a background trigger, so the activity needs its own task.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            LaunchResult.Opened
        } catch (e: SecurityException) {
            LaunchResult.Failed("Android wouldn't let TapRelay open that.")
        } catch (e: Exception) {
            LaunchResult.Failed("Nothing on this phone can open that.")
        }
    }
}

sealed interface LaunchResult {
    data object Opened : LaunchResult
    data class Failed(val message: String) : LaunchResult
}
