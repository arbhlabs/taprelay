package com.arbhlabs.taprelay.band

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.arbhlabs.taprelay.R
import com.arbhlabs.taprelay.ui.MainActivity

/**
 * Keeps TapRelay's process alive so the Xiaomi band can reach [BandBridge] with TapRelay closed.
 * Without it Android freezes the cached process seconds after the app leaves the screen and every
 * band message is dropped. Only started when Notify for Xiaomi (the band broker) is installed.
 */
class BandLinkService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Band link", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps your Xiaomi band connected to TapRelay"
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_qs_remote)
            .setContentTitle("Band link active")
            .setContentText("Your band can control TapRelay")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        runCatching {
            ServiceCompat.startForeground(this, ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        }.onFailure {
            Log.w("TapRelayBand", "link service could not go foreground: $it")
            stopSelf()
        }
        return START_STICKY
    }

    companion object {
        private const val CHANNEL = "band_link"
        private const val ID = 0xBA9D

        fun startIfBandBroker(context: Context) {
            val broker = listOf("com.mc.xiaomi1", "com.mc.xiaomi1.huawei").any {
                runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess
            }
            if (!broker) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, BandLinkService::class.java)) }
                .onFailure { Log.w("TapRelayBand", "link service start refused: $it") }
        }
    }
}
