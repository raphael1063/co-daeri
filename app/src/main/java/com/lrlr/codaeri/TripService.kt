package com.lrlr.codaeri

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * 기사님을 기다리는 동안과 운행 중에만 화면이 가려져도 운행을 기록하게 프로세스를 살려 두는 서비스. 할 일이 끝나면 내려간다.
 * 일은 DaeriController가 하고, 이 서비스는 프로세스를 살려 두기만 한다.
 */
class TripService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_trip), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_recording))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID, n)
            }
        } catch (e: Exception) {
            // 백그라운드 재시작 등으로 포그라운드 전환이 거부되면 조용히 멈춘다(운행 기록은 앱 상태에 남아 있다)
            stopSelf()
            return START_NOT_STICKY
        }
        // 앱 객체가 컨트롤러를 띄운다(재시작 시 운행 이어 가기 포함). 할 일이 없으면 알림을 남기지 않고 멈춘다.
        if (!(application as DaeriApp).controller.needsKeepAlive()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private companion object {
        const val CHANNEL = "trip"
        const val ID = 1
    }
}
