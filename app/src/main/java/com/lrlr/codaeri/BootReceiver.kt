package com.lrlr.codaeri

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 시동 뒤(또는 앱 업데이트 뒤) 끊긴 운행 기록을 이어 간다.
 * 이어 갈 운행도 기다리는 배차도 없으면 서비스를 띄우지 않는다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> try {
                // 이어 갈 운행이나 기다리는 배차가 있을 때만 서비스를 띄운다. 없으면 아무것도 하지 않는다.
                val app = context.applicationContext as DaeriApp
                if (app.controller.needsKeepAlive()) context.startForegroundService(Intent(context, TripService::class.java))
            } catch (e: Exception) {
                Log.w("DaeriBoot", "service start refused", e)
            }
        }
    }
}
