package com.lrlr.codaeri

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 시동 뒤(또는 앱 업데이트 뒤) 화면을 열지 않아도 기록 서비스가 뜨게 한다.
 * 번호와 묶이지 않은 차에서는 서비스가 스스로 멈춘다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> try {
                context.startForegroundService(Intent(context, TripService::class.java))
            } catch (e: Exception) {
                Log.w("DaeriBoot", "service start refused", e)
            }
        }
    }
}
