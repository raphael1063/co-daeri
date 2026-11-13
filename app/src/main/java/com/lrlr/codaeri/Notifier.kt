package com.lrlr.codaeri

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lrlr.codaeri.app.Notices
import com.lrlr.codaeri.R
import com.lrlr.codaeri.domain.text

/**
 * 화면이 가려져 있을 때만 띄우는 알림 두 가지: 예약이 확인됐다는 것, 운행이 닫혔다는 것.
 * 달리는 동안에는 띄우지 않는다.
 * Pleos는 종류(category)를 메시지로 준 알림만 화면 위에 띄운다(에뮬레이터에서 확인).
 */
class Notifier(private val context: Context) : Notices {
    private val nm = context.getSystemService(NotificationManager::class.java)

    override fun enabled(): Boolean = nm.areNotificationsEnabled()

    private fun post(id: Int, title: String, text: String, category: String) {
        if (!enabled()) return
        try {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_handover), NotificationManager.IMPORTANCE_HIGH),
            )
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(category)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(id, n)
        } catch (e: Exception) {
            Log.w(TAG, "notify failed", e)
        }
    }

    override fun booking(driverName: String, destination: String) {
        // 서비스가 차주에게 보내는 소식이므로 메시지로 알린다.
        post(ID_BOOKING, text(R.string.notify_booking_title), text(R.string.notify_booking_text, driverName, destination), Notification.CATEGORY_MESSAGE)
    }

    override fun closed(headline: String) {
        // 상태를 남기는 알림이라 화면 위로 띄우지 않고 알림 센터에 둔다.
        post(ID_CLOSED, text(R.string.notify_closed_title), headline, Notification.CATEGORY_STATUS)
    }

    /** 화면을 열면 알림은 할 일을 다했다 */
    override fun clear() {
        try { nm.cancel(ID_BOOKING); nm.cancel(ID_CLOSED) } catch (_: Exception) {}
    }

    private companion object {
        const val TAG = "DaeriNotifier"
        const val CHANNEL = "handover"
        const val ID_BOOKING = 2
        const val ID_CLOSED = 3
    }
}
