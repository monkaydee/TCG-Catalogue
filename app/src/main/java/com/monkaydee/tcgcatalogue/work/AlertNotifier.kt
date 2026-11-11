package com.monkaydee.tcgcatalogue.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.monkaydee.tcgcatalogue.MainActivity
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.PriceAlert
import com.monkaydee.tcgcatalogue.ui.AppStrings

/** Shows price alerts (a card rose above / fell below its alert price, a wished card got cheap). */
object AlertNotifier {
    private const val CHANNEL = "price_alerts"

    fun show(context: Context, alerts: List<PriceAlert>) {
        if (alerts.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val strings = AppStrings.context()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, strings.getString(R.string.alert_channel), NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val manager = NotificationManagerCompat.from(context)
        alerts.take(20).forEachIndexed { i, a ->
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_alert)
                .setContentTitle(a.name)
                .setContentText(a.detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(a.detail))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(1000 + i, n) }
        }
    }
}
