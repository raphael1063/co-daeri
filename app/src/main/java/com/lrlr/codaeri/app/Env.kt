package com.lrlr.codaeri.app

import com.lrlr.codaeri.domain.Announcer
import com.lrlr.codaeri.domain.CarSignals
import com.lrlr.codaeri.domain.DaeriService
import com.lrlr.codaeri.domain.DrivingRestriction
import com.lrlr.codaeri.domain.LinkFactory
import com.lrlr.codaeri.domain.LocationSource
import com.lrlr.codaeri.domain.RouteGuide
import com.lrlr.codaeri.domain.SpeechListener
import com.lrlr.codaeri.domain.StateRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/** 흐름들이 함께 쓰는 실행 환경: 코루틴 범위, 통신용 디스패처, 시계. 시험에서는 가짜로 바꾼다. */
class Env(val scope: CoroutineScope, val io: CoroutineDispatcher, val clock: () -> Long)

/** 포트의 구현 묶음. 실제 구현은 AppContainer가, 시험에서는 가짜가 채운다. */
class Adapters(
    val repository: StateRepository,
    val service: DaeriService,
    val links: LinkFactory,
    val car: CarSignals,
    val location: LocationSource,
    val restriction: DrivingRestriction,
    val announcer: Announcer,
    val listener: SpeechListener,
    val routeGuide: RouteGuide,
    val notices: Notices,
    val keepAlive: KeepAlive,
    val connectivity: Connectivity,
)
