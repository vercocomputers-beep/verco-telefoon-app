package org.verco.telefoon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Houdt de app aangemeld bij de centrale terwijl hij op de achtergrond staat (Android sluit de app anders),
 * en toont een melding bij een inkomend gesprek (ook op het vergrendelscherm).
 * Later komt hier push (Firebase) bij, zodat de service niet altijd hoeft te draaien.
 */
class PhoneService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channels(this)
        val n = NotificationCompat.Builder(this, CH_STATUS)
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle("Verco Telefoon")
            .setContentText("Bereikbaar op je toestelnummer")
            .setOngoing(true)
            .setContentIntent(openApp(this))
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(ID_STATUS, n)
        }
        return START_STICKY
    }

    companion object {
        private const val CH_STATUS = "status"
        private const val CH_CALL = "call"
        private const val ID_STATUS = 1
        private const val ID_CALL = 2

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, PhoneService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PhoneService::class.java))
        }

        private fun channels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_STATUS, "Bereikbaar", NotificationManager.IMPORTANCE_MIN))
            nm.createNotificationChannel(NotificationChannel(CH_CALL, "Inkomende gesprekken", NotificationManager.IMPORTANCE_HIGH))
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        /** Inkomend gesprek: melding die het scherm aanzet en de app opent. */
        fun showIncoming(context: Context, who: String) {
            channels(context)
            val n = NotificationCompat.Builder(context, CH_CALL)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle("Inkomend gesprek")
                .setContentText(who)
                .setCategory(Notification.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setFullScreenIntent(openApp(context), true)
                .setContentIntent(openApp(context))
                .build()
            runCatching { context.getSystemService(NotificationManager::class.java).notify(ID_CALL, n) }
        }

        fun showOngoing(context: Context, who: String) {
            channels(context)
            val n = NotificationCompat.Builder(context, CH_CALL)
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle("In gesprek")
                .setContentText(who)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(openApp(context))
                .build()
            runCatching { context.getSystemService(NotificationManager::class.java).notify(ID_CALL, n) }
        }

        fun callFinished(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(ID_CALL)
        }
    }
}
