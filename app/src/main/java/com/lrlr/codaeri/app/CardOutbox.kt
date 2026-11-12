package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.DaeriService
import com.lrlr.codaeri.domain.Handover
import com.lrlr.codaeri.domain.TripPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 귀가 카드를 서비스에 보낸다. 보낼 카드는 상태의 outbox 한 장이고, 실패하면 통신이 돌아올 때까지 다시 시도한다.
 * 종료 후보를 보냈을 때 서비스가 종료를 확정하면 그 시각을 카드에 적는다.
 */
class CardOutbox(
    private val store: AppStateStore,
    private val service: DaeriService,
    private val handover: Handover,
    private val env: Env,
    /** 서비스가 종료를 확정했다(결제 상태를 다시 묻는다) */
    private val onConfirmed: suspend () -> Unit,
) {
    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading
    private var retryJob: Job? = null

    /** 운행 중 카드(출발, 도착)를 미리 올린다. 마무리 전송이 실패해도 휴대폰에 도착 정보가 남는다. */
    fun queueProgress() {
        val s = store.value
        val trip = s.trip ?: return
        if (trip.phase == TripPhase.DONE || trip.booking == null) return
        val card = handover.progressCard(s.card, trip, env.clock())
        store.update { it.queued(card) }
        send()
    }

    /** 보낼 카드가 있으면 보낸다. 이미 보내는 중이면 끝난 뒤 바뀐 카드를 이어 보낸다. */
    fun send() {
        val s = store.value
        val link = s.phoneLink ?: return
        val card = s.outbox ?: return
        if (!s.uploadPending || _uploading.value) return
        _uploading.value = true
        env.scope.launch {
            val result = try { withContext(env.io) { service.upload(link, card) } } finally { _uploading.value = false }
            val confirmedAt = result?.confirmedAt
            if (confirmedAt != null) {
                store.update { st -> if (st.lastCard?.tripId == card.tripId) st.copy(lastCard = st.lastCard.copy(confirmedAt = confirmedAt)) else st }
                env.scope.launch { onConfirmed() }
            }
            when {
                result != null && store.value.outbox == card -> store.update { it.copy(uploadPending = false, lastUploadAt = env.clock()) }
                result != null -> send() // 올리는 사이에 카드가 바뀜(잠금 갱신, 마무리)
                else -> scheduleRetry()
            }
        }
    }

    /** 끊겼던 통신이 돌아오면 다시 보낸다. */
    fun watch(connectivity: Connectivity) {
        connectivity.onAvailable { env.scope.launch { send() } }
    }

    /** 지하처럼 망은 잡혀도 서버에 닿지 않는 경우를 위해 30초마다 다시 시도한다. */
    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        retryJob = env.scope.launch {
            delay(RETRY_MS)
            send()
        }
    }

    companion object {
        const val RETRY_MS = 30_000L
    }
}
