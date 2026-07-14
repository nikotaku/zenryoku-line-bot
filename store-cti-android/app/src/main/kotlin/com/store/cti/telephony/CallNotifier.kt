package com.store.cti.telephony

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.store.cti.R
import com.store.cti.core.model.CustomerCategory
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2: 着信スクリーニング結果の通知。タップで着信対応画面
 * (storecti://intake?phone=...)を開く。通知が許可されていない場合は何もしない。
 */
@Singleton
class CallNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyIncomingCall(rawPhone: String, customer: CustomerEntity?) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            AppLogger.w("notifications disabled; skip incoming call notification")
            return
        }
        ensureChannel()

        val title = if (customer != null) {
            val category = CustomerCategory.fromName(customer.category)
            "着信: ${customer.name} 様(${category.label})"
        } else {
            "着信: 未登録の番号"
        }
        val text = buildString {
            if (customer != null) {
                append("利用${customer.visitCount}回")
                if (!customer.lastVisitDate.isNullOrBlank()) append(" / 前回 ${customer.lastVisitDate}")
                if (customer.cautionNotes.isNotBlank()) append("\n注意: ${customer.cautionNotes}")
            } else {
                append("タップして対応画面を開く")
            }
        }

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("storecti://intake?phone=${Uri.encode(rawPhone)}"),
        ).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = PendingIntent.getActivity(
            context, rawPhone.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shortcut_phone)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (se: SecurityException) {
            AppLogger.w("notify failed: permission not granted")
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "着信情報", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "着信時に顧客情報を表示します"
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "incoming_call"
        private const val NOTIFICATION_ID = 1001
    }
}
