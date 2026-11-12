package com.lrlr.codaeri.car

import com.lrlr.codaeri.domain.SpeechListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * 한 번 누르면 한 문장을 듣는다(Pleos SpeechToText, 차 안에서 인식). 누르지 않으면 마이크를 쓰지 않고,
 * 들은 소리와 글은 저장하지 않는다.
 */
class VoiceInput(private val context: Context) : SpeechListener {
    private val main = Handler(Looper.getMainLooper())
    private var stt: SpeechToText? = null
    private var ready = false
    private var onResult: ((String?) -> Unit)? = null
    private var last = ""
    /** 지금 듣기가 실제로 시작됐는지. 앞선 듣기를 멈출 때 늦게 오는 "끝" 알림이 새 듣기를 끝내지 않게 한다. */
    private var armed = false
    private var stoppedAt = 0L
    private val arm = Runnable { armed = true }
    private var starter: Runnable? = null

    private val _partial = MutableStateFlow("")
    /** 듣는 동안 화면에 보여 줄, 지금까지 알아들은 말 */
    override val partial: StateFlow<String> = _partial

    private val timeout = Runnable { finish(last.ifBlank { null }) }

    private val listener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            main.post {
                if (onResult == null) return@post
                // 마지막 알림이 빈 글로 오는 경우가 있어, 그 전에 알아들은 말을 지우지 않는다
                if (text.isNotBlank()) { last = text; _partial.value = text }
                if (isFinal) finish(last.ifBlank { null })
            }
        }
        override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) {}
        override fun onStartedRecognition() { main.post { if (onResult != null) armed = true } }
        override fun onEndedRecognition() {
            main.post {
                if (!armed) return@post
                val session = onResult
                main.postDelayed({ if (onResult != null && onResult === session) finish(last.ifBlank { null }) }, 600)
            }
        }
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "stt error $errorType")
            main.post { if (armed) finish(last.ifBlank { null }) }
        }
        override fun onReady() {}
        override fun onUpdatedRms(rms: Double) {}
    }

    /** 한 문장을 듣고 결과를 돌려준다. 못 들었거나 음성 인식을 쓸 수 없으면 null. */
    override fun listen(result: (String?) -> Unit) {
        cancel()
        onResult = result
        last = ""
        _partial.value = ""
        main.postDelayed(timeout, LISTEN_MS)
        try {
            val engine = stt ?: SpeechToText(context.applicationContext, Mode.ON_DEVICE).also { stt = it }
            if (ready) {
                // 방금 멈춘 듣기가 정리될 틈을 준 뒤 다시 듣는다
                val wait = (stoppedAt + RESTART_GAP_MS - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0)
                val start = Runnable { if (onResult === result) request(engine) }
                starter = start
                main.postDelayed(start, wait)
                return
            }
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        try {
                            engine.setLocale(Locale.KOREA)
                            engine.addListener(listener)
                            ready = true
                            if (onResult != null) request(engine)
                        } catch (t: Throwable) {
                            Log.w(TAG, "stt start failed", t)
                            finish(null)
                        }
                    }
                }
                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "stt init failed $errorType")
                    main.post { finish(null) }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "stt unavailable", t)
            finish(null)
        }
    }

    private fun request(engine: SpeechToText) {
        armed = false
        main.removeCallbacks(arm)
        main.postDelayed(arm, ARM_MS) // 시작 알림이 오지 않는 경우에 대비
        try { engine.request() } catch (t: Throwable) { Log.w(TAG, "stt request failed", t); finish(null) }
    }

    /** 듣기를 그만둔다. 결과는 돌려주지 않는다. */
    override fun cancel() {
        val wasListening = onResult != null
        onResult = null
        armed = false
        main.removeCallbacks(timeout)
        main.removeCallbacks(arm)
        starter?.let(main::removeCallbacks)
        starter = null
        if (ready && wasListening) {
            stoppedAt = android.os.SystemClock.uptimeMillis()
            try { stt?.stop() } catch (_: Throwable) {}
        }
    }

    private fun finish(text: String?) {
        val cb = onResult ?: return
        cancel()
        Log.i(TAG, "heard: ${text?.length ?: 0} chars")
        cb(text)
    }

    companion object {
        private const val TAG = "DaeriVoice"
        private const val LISTEN_MS = 10_000L
        private const val RESTART_GAP_MS = 700L
        private const val ARM_MS = 1_500L
    }
}
