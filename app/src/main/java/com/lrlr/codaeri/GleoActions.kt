package com.lrlr.codaeri

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Gleo AI 연동(assets/schema.json): "어젯밤 대리 기록 알려 줘" 한 가지 질문에 답한다.
 * 스키마는 등록 절차를 거쳐야 켜지므로, 그 전에는
 * 향후 계획이라 매니페스트에 등록하지 않았다. 등록할 때는 부르는 쪽을 권한으로 제한한다.
 */
class GleoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val answer = (context.applicationContext as DaeriApp).controller.lastTripAnswer()
        val json = response(answer)
        try {
            context.sendBroadcast(Intent(RESPONSE).putExtra("packageName", context.packageName).putExtra("response", json))
        } catch (e: Exception) {
            Log.w("DaeriGleo", "callback failed", e)
        }
    }

    companion object {
        const val ACTION = "GET_LAST_DAERI_TRIP"
        private const val RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"

        fun response(answer: String?): String = buildJsonObject {
            put("functionName", ACTION)
            put("status", if (answer != null) "success" else "fail")
            put("message", answer ?: "대리 운행 기록이 없습니다.")
        }.toString()
    }
}
