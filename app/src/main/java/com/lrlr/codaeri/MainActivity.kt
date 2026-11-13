package com.lrlr.codaeri

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lrlr.codaeri.app.CallStage
import com.lrlr.codaeri.domain.VoiceCall
import com.lrlr.codaeri.ui.screens.BindScreen
import com.lrlr.codaeri.R
import com.lrlr.codaeri.ui.ButtonSize
import com.lrlr.codaeri.ui.CallChoice
import com.lrlr.codaeri.ui.Divider
import com.lrlr.codaeri.ui.Fact
import com.lrlr.codaeri.ui.PairColumns
import com.lrlr.codaeri.ui.screens.CardEditorScreen
import com.lrlr.codaeri.ui.screens.ClosedScreen
import com.lrlr.codaeri.ui.screens.ParkingEditPopup
import com.lrlr.codaeri.ui.screens.SplitTip
import com.lrlr.codaeri.ui.screens.StartPinPopup
import com.lrlr.codaeri.ui.DaeriTheme
import com.lrlr.codaeri.ui.screens.DrivingScreen
import com.lrlr.codaeri.ui.Fading
import com.lrlr.codaeri.ui.screens.HandoverScreen
import com.lrlr.codaeri.ui.screens.HomeScreen
import com.lrlr.codaeri.ui.Ivi
import com.lrlr.codaeri.ui.IviButton
import com.lrlr.codaeri.ui.IviPopup
import com.lrlr.codaeri.ui.Motion
import com.lrlr.codaeri.ui.screens.OtherSpotScreen
import com.lrlr.codaeri.ui.PopupTitle
import com.lrlr.codaeri.ui.Route
import com.lrlr.codaeri.ui.Screen
import com.lrlr.codaeri.ui.Section
import com.lrlr.codaeri.ui.screens.RecordsScreen
import com.lrlr.codaeri.ui.screens.SettingsScreen
import com.lrlr.codaeri.ui.screens.StopFirst
import com.lrlr.codaeri.ui.T
import com.lrlr.codaeri.ui.Type
import com.lrlr.codaeri.ui.screens.WrapScreen

class MainActivity : ComponentActivity() {

    /** 지금 보여 줄 화면. 운행 상태가 화면을 정하고, 사람이 고르는 것은 주차 안내 편집뿐이다. */
    private val controller get() = (application as DaeriApp).controller
    private val noticeAllowed = mutableStateOf(true)
    // 허락하지 않아도 한 번은 시도한다(인식은 차의 음성 서비스가 한다). 안 되면 화면에 "듣지 못했습니다"가 뜬다.
    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { controller.callByVoice() }
    private val askNotice = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        noticeAllowed.value = controller.noticeAllowed()
    }

    // 화면이 켜져 있어도 다른 창(후방 카메라, 내비게이션)에 가려져 초점이 없으면 보이지 않는 것으로 본다.
    // 그래야 가려진 동안 지도 위 표시가 뜬다.
    private var started = false
    private var focused = false
    private fun syncVisible() { controller.visible.value = started && focused }

    override fun onStart() {
        super.onStart()
        started = true
        syncVisible()
        noticeAllowed.value = controller.noticeAllowed()
    }

    override fun onStop() {
        started = false
        syncVisible()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        focused = hasFocus
        syncVisible()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val c = controller

        setContent {
            DaeriTheme {
                val state by c.state.collectAsStateWithLifecycle()
                val guidance by c.guidance.collectAsStateWithLifecycle()
                val restricted by c.restricted.collectAsStateWithLifecycle()
                val speaking by c.speaking.collectAsStateWithLifecycle()
                val uploading by c.uploading.collectAsStateWithLifecycle()
                val busy by c.busy.collectAsStateWithLifecycle()
                val location by c.position.collectAsStateWithLifecycle()
                val pairing by c.pairing.collectAsStateWithLifecycle()
                val voice by c.voice.collectAsStateWithLifecycle()
                val emergencyUi by c.emergency.collectAsStateWithLifecycle()
                val startArmed by c.startArmed.collectAsStateWithLifecycle()
                val startReady by c.startReady.collectAsStateWithLifecycle()
                val shareBusy by c.shareBusy.collectAsStateWithLifecycle()
                val shareMessage by c.shareMessage.collectAsStateWithLifecycle()
                val judgesBoarding by c.judgesBoarding.collectAsStateWithLifecycle()
                val voicePartial by c.voicePartial.collectAsStateWithLifecycle()
                var editing by rememberSaveable { mutableStateOf(false) }
                val canCall by c.canCall.collectAsStateWithLifecycle()
                val cancellingBooking by c.cancellingBooking.collectAsStateWithLifecycle()
                val parking by c.parking.collectAsStateWithLifecycle()
                val cancelBookingMessage by c.cancelBookingMessage.collectAsStateWithLifecycle()
                var confirmCancel by rememberSaveable { mutableStateOf(false) }
                // 설정에서 고치고 있는 연락처 묶음
                var contacts by rememberSaveable { mutableStateOf<com.lrlr.codaeri.domain.ContactKind?>(null) }
                var regularOpen by rememberSaveable { mutableStateOf(false) }
                val regularBusy by c.regularBusy.collectAsStateWithLifecycle()
                val regularMessage by c.regularMessage.collectAsStateWithLifecycle()
                val contactBusy by c.contactBusy.collectAsStateWithLifecycle()
                val contactMessage by c.contactMessage.collectAsStateWithLifecycle()
                var otherSpot by rememberSaveable { mutableStateOf(false) }
                var editParking by rememberSaveable { mutableStateOf(false) }
                var confirmUnbind by rememberSaveable { mutableStateOf(false) }
                var section by rememberSaveable { mutableStateOf(Section.MAIN) }
                // 연결이 풀리면 해제 확인창도 닫는다(다시 연결했을 때 예전 창이 남아 있지 않게)
                LaunchedEffect(state.bound) { if (!state.bound) confirmUnbind = false }

                val trip = state.trip
                val routed by c.routed.collectAsStateWithLifecycle()
                // 예약의 유효 시간은 화면이 다시 그려질 때마다 본다(상태가 15초마다 갱신된다)
                val pending = state.pendingBooking(System.currentTimeMillis())
                // 예약 없이 달리는 주행(차주의 평소 주행일 수 있다)은 화면을 바꾸지 않는다. 뒤늦게 예약이 확인되면 그때 운행 화면이 뜬다.
                val screen = Route.screen(
                    tripPhase = trip?.takeIf { it.booking != null }?.phase, hasCard = state.lastCard != null, bound = state.bound, restricted = restricted,
                    editing = editing, otherSpot = otherSpot, section = section, hasBooking = pending != null,
                )
                // 예약이 잡히거나 기사님이 바뀌면 배차 현황을 연다
                LaunchedEffect(pending?.id, pending?.driver?.id) { section = Route.onBookingChanged(section, pending != null, restricted) }
                // 동승 신호가 있으면 메인의 "출발하기"를 크게 보여 준다
                LaunchedEffect(startReady) { if (startReady) { section = Section.MAIN; editing = false; contacts = null; regularOpen = false } }
                // 운행이 시작되면 메인에서 열어 둔 화면은 닫는다
                LaunchedEffect(trip?.id) { if (trip != null) { section = Section.MAIN; editing = false; contacts = null; regularOpen = false } }
                if (screen != Screen.OTHER_SPOT && screen != Screen.WRAP && screen != Screen.STOP_FIRST) otherSpot = false

                Box(Modifier.fillMaxSize().background(Ivi.colors.backgroundPrimary).systemBarsPadding()) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            // 달리는 화면으로 넘어갈 때는 움직임을 쓰지 않는다
                            if (targetState == Screen.DRIVING) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            else fadeIn(tween(Motion.ENTER)) togetherWith fadeOut(tween(Motion.EXIT))
                        },
                        label = "screen",
                    ) { target ->
                        when (target) {
                            Screen.DRIVING -> trip?.let {
                                DrivingScreen(it, state.card, guidance, c.handover, speaking, restricted, c::nextGuide, c::repeatGuide, onMap = ::backToMap, emergencies = state.server.emergencyList, onEmergency = c::showEmergency, parking = parking)
                            }
                            Screen.STOP_FIRST -> StopFirst(if (trip != null) "운행을 마무리해 주세요" else "번호를 연결해 주세요")
                            Screen.WRAP -> trip?.let {
                                WrapScreen(
                                    trip = it, card = state.card, handover = c.handover, seatCloses = c.seatCloses(), busy = busy,
                                    emergencies = state.server.emergencyList,
                                    onParking = c::chooseParking, onOtherSpot = { otherSpot = true }, onClose = c::closeByButton, onEmergency = c::showEmergency, onMap = ::backToMap,
                                )
                            }
                            Screen.OTHER_SPOT -> {
                                val dictation by c.spotDictation.collectAsStateWithLifecycle()
                                val heard by c.voicePartial.collectAsStateWithLifecycle()
                                BackHandler { c.resetDictation(); otherSpot = false }
                                OtherSpotScreen(
                                    state.card, c.handover, dictation, heard,
                                    onDictate = c::dictateSpot, onStopDictation = c::stopDictation, onClearDictation = c::resetDictation,
                                    onResult = { c.chooseParking(it); c.resetDictation(); otherSpot = false },
                                    onBack = { c.resetDictation(); otherSpot = false },
                                )
                            }
                            Screen.CLOSED -> state.lastCard?.let { ClosedScreen(it, state, c.handover, uploading, c::closeTrip, onEditParking = { editParking = true }) }
                            Screen.BIND -> BindScreen(state.consentAt != null, pairing.url, pairing.status, pairing.short, c::agreeToCollection, c::newPairCode)
                            Screen.EDITOR -> {
                                BackHandler { editing = false }
                                CardEditorScreen(
                                    initial = state.card,
                                    currentLocation = location?.let { it.lat to it.lng },
                                    onSave = { c.saveCard(it); editing = false },
                                    onBack = { editing = false },
                                )
                            }
                            Screen.HANDOVER -> {
                                BackHandler { section = Section.MAIN }
                                pending?.let { HandoverScreen(it, c.handover, state.server.phone, routed == it.id, armed = startArmed == it.id, restricted = restricted, manualStart = !judgesBoarding, onStart = c::pressStart, cancelling = cancellingBooking, cancelMessage = cancelBookingMessage, onCancel = { confirmCancel = true }, onBack = { section = Section.MAIN }) }
                            }
                            Screen.RECORDS -> {
                                BackHandler { section = Section.MAIN }
                                RecordsScreen(state, c.handover, uploading, regularBusy, regularMessage, onSetRegular = { c.setRegular(it) }) { section = Section.MAIN }
                            }
                            Screen.SETTINGS -> contacts?.let { kind ->
                                val close = { c.clearContactMessage(); contacts = null }
                                BackHandler { close() }
                                com.lrlr.codaeri.ui.screens.ContactsScreen(
                                    kind,
                                    if (kind == com.lrlr.codaeri.domain.ContactKind.EMERGENCY) state.server.emergencyList else state.server.share.contacts,
                                    contactBusy, contactMessage,
                                    onAdd = { label, phone -> c.addContact(kind, label, phone) },
                                    onRemove = { c.removeContact(kind, it) },
                                    onBack = close,
                                )
                            } ?: if (regularOpen) {
                                val close = { c.clearRegularMessage(); regularOpen = false }
                                BackHandler { close() }
                                com.lrlr.codaeri.ui.screens.RegularScreen(
                                    state.server.regular, com.lrlr.codaeri.app.RegularEditor.candidates(state), regularBusy, regularMessage,
                                    onPick = { c.setRegular(it) }, onClear = { c.setRegular(null) }, onBack = close,
                                )
                            } else run {
                                BackHandler { section = Section.MAIN }
                                SettingsScreen(
                                    state = state,
                                    onBack = { section = Section.MAIN },
                                    onEditCard = { editing = true },
                                    onUnbind = { confirmUnbind = true },
                                    noticeOn = noticeAllowed.value && !state.noticesOff,
                                    onToggleNotice = {
                                        if (noticeAllowed.value && !state.noticesOff) c.setNotices(false)
                                        else {
                                            c.setNotices(true)
                                            if (!noticeAllowed.value && Build.VERSION.SDK_INT >= 33) askNotice.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    },
                                    shareMessage = shareMessage,
                                    // 바꾸는 동안에도 체크박스는 그대로 두고, 누른 것만 받지 않는다.
                                    onToggleShare = { if (!restricted) c.toggleShare() },
                                    onEditContacts = { contacts = it },
                                    onEditRegular = { regularOpen = true }, canPickRegular = com.lrlr.codaeri.app.RegularEditor.candidates(state).isNotEmpty(),
                                )
                            }
                            Screen.HOME -> HomeScreen(
                                booked = pending != null,
                                restricted = restricted,
                                onCall = c::startCall,
                                onStatus = { section = Section.STATUS },
                                onRecords = { section = Section.RECORDS },
                                onSettings = { section = Section.SETTINGS },
                                startLine = pending?.takeIf { startReady }?.let { "${it.driver.name} 기사님 · ${it.destination} · ${c.handover.fareText(it.fare)}" },
                                armed = pending != null && startArmed == pending.id,
                                onStart = c::pressStart,
                                canCall = canCall,
                                cancelling = cancellingBooking, cancelMessage = cancelBookingMessage,
                                onCancel = { confirmCancel = true },
                            )
                        }
                    }

                    // 배차 현황과 운행 중 화면에서는 내비게이션과 나란히 보는 방법을 도움말로 한 번 알린다.
                    val tipKey = (pending?.id ?: trip?.booking?.id)?.takeIf { screen == Screen.HANDOVER || screen == Screen.DRIVING }
                    if (tipKey != null) SplitTip(tipKey, still = restricted)

                    // 출발하기를 누르면 기사님 PIN을 묻는다. 예약이 사라지거나 운행이 시작되면 창도 닫는다.
                    val startPin by c.startPin.collectAsStateWithLifecycle()
                    LaunchedEffect(pending?.id, trip?.id) { if (pending == null || trip != null) c.dismissStartPin() }
                    Fading(startPin) { ui -> StartPinPopup(ui.error, pending?.driver?.name.orEmpty(), c::submitStartPin, c::dismissStartPin) }

                    // 목적지에서 먼 곳에서 누른 운행 종료는 한 번 더 묻는다.
                    val closeAsk by c.closeAsk.collectAsStateWithLifecycle()
                    LaunchedEffect(trip?.id, screen) { if (trip == null || screen != Screen.WRAP) c.dismissCloseAsk() }
                    Fading(closeAsk) { ask ->
                        IviPopup(
                            onDismiss = c::dismissCloseAsk,
                            buttons = {
                                IviButton("돌아가기", c::dismissCloseAsk, Modifier.weight(1f), size = ButtonSize.LARGE)
                                IviButton("운행 종료", c::confirmClose, Modifier.weight(1f), danger = true, size = ButtonSize.LARGE)
                            },
                        ) {
                            PopupTitle(
                                "목적지에서 먼 곳입니다. 정말 운행을 종료할까요?",
                                "목적지 ${ask.destination}까지 직선거리로 약 ${c.handover.distanceText(ask.km)} 남았습니다. 잠깐 선 것이라면 돌아가기를 눌러 주세요. 종료하면 이 운행은 여기서 끝납니다.",
                            )
                        }
                    }

                    // 호출 취소는 한 번 더 묻는다. 예약이 사라지면(취소됨, 출발함) 창도 닫는다.
                    LaunchedEffect(pending?.id, trip?.id) { if (pending == null || trip != null) confirmCancel = false }
                    Fading(pending?.takeIf { confirmCancel && (screen == Screen.HANDOVER || screen == Screen.HOME) }) { b ->
                        IviPopup(
                            onDismiss = { confirmCancel = false },
                            buttons = {
                                IviButton("돌아가기", { confirmCancel = false }, Modifier.weight(1f), size = ButtonSize.LARGE)
                                IviButton("호출 취소", { confirmCancel = false; c.cancelBooking() }, Modifier.weight(1f), danger = true, size = ButtonSize.LARGE)
                            },
                        ) {
                            PopupTitle("호출을 취소할까요?", "${b.driver.name} 기사님 배정이 취소됩니다. ${b.destination}까지의 예약이 없어집니다.")
                        }
                    }

                    Fading(voice.takeIf { it.stage != CallStage.OFF && screen == Screen.HOME }) { v ->
                        val listening = v.stage == CallStage.LISTENING
                        val confirm = v.stage == CallStage.CONFIRM
                        val choosing = v.stage == CallStage.CHOOSE || v.stage == CallStage.PLACES
                        // 마이크는 "말로 입력"을 고를 때 처음 허락을 받는다
                        val byVoice = {
                            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) c.callByVoice()
                            else askMic.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        IviPopup(
                            onDismiss = c::cancelCall,
                            width = if (choosing) 1000.dp else if (confirm && v.place != null) 960.dp else 760.dp,
                            buttons = {
                                // 집 위치가 없을 때는 막다른 "취소" 대신 갈 수 있는 두 길을 준다: 다른 방법, 집 설정하기
                                if (v.homeMissing) IviButton("다른 방법", c::startCall, Modifier.weight(1f), size = ButtonSize.LARGE)
                                else IviButton("취소", c::cancelCall, Modifier.weight(1f), size = ButtonSize.LARGE)
                                when {
                                    v.homeMissing -> IviButton("설정하기", { c.cancelCall(); section = Section.SETTINGS; editing = true }, Modifier.weight(1f), filled = true, size = ButtonSize.LARGE)
                                    v.stage == CallStage.CHOOSE -> {}
                                    v.stage == CallStage.PLACES -> IviButton("다른 방법", c::startCall, Modifier.weight(1f), size = ButtonSize.LARGE)
                                    confirm && v.place != null -> IviButton("부르기", c::confirmCall, Modifier.weight(1f), filled = true, size = ButtonSize.LARGE)
                                    confirm && v.byVoice -> IviButton("다시 말하기", byVoice, Modifier.weight(1f), filled = true, size = ButtonSize.LARGE)
                                    confirm -> IviButton("다른 방법", c::startCall, Modifier.weight(1f), filled = true, size = ButtonSize.LARGE)
                                    else -> IviButton(if (listening) "듣는 중" else "확인 중", {}, Modifier.weight(1f), filled = true, size = ButtonSize.LARGE, loading = !listening, enabled = false)
                                }
                            },
                        ) {
                            val asking = confirm && v.place != null
                            PopupTitle(
                                if (asking) VoiceCall.CONFIRM_TITLE else v.message,
                                when {
                                    v.stage == CallStage.CHOOSE -> "편한 방법으로 목적지를 입력해 주세요."
                                    v.stage == CallStage.PLACES -> "시연에서는 아래 장소만 고를 수 있습니다."
                                    listening -> voicePartial.ifBlank { VoiceCall.EXAMPLE }
                                    // 다시 묻는 말이나 통신 오류가 있으면 그것을 먼저 보여 준다
                                    asking && v.message != v.asked -> v.message
                                    asking -> if (v.byVoice) VoiceCall.ANSWER_HINT else VoiceCall.TAP_HINT
                                    v.homeMissing -> com.lrlr.codaeri.app.CallFlow.HOME_MISSING_HINT
                                    else -> null
                                },
                            )
                            if (asking && v.place != null) {
                                Spacer(Modifier.height(20.dp))
                                Divider()
                                Spacer(Modifier.height(10.dp))
                                Fact("출발지", v.from, valueStyle = Type.bodyLarge)
                                Fact("목적지", v.place.name, valueStyle = Type.bodyLarge)
                                Fact("예상 거리", v.km?.let { "약 ${c.handover.distanceText(it)} (직선 거리 기준)" } ?: "현재 위치를 확인하지 못했습니다", valueStyle = Type.bodyLarge)
                                Fact("확정 요금", "${c.handover.fareText(v.fare ?: 0)} (시연용)", valueStyle = Type.bodyLarge)
                            }
                            if (choosing) {
                                Spacer(Modifier.height(28.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    if (v.stage == CallStage.CHOOSE) {
                                        CallChoice("집", R.drawable.ic_call_home, c::callHome, Modifier.weight(1f))
                                        CallChoice("목적지 선택", R.drawable.ic_call_place, c::showPlaces, Modifier.weight(1f))
                                        CallChoice("말로 입력", R.drawable.ic_call_voice, byVoice, Modifier.weight(1f))
                                    } else {
                                        VoiceCall.places.forEach { place -> CallChoice(place.name, R.drawable.ic_call_place, { c.callTo(place) }, Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }

                    // 비상연락처는 운행 중(달리는 화면과 정차 중 화면)에만 보인다. 운행이 끝나면 닫는다.
                    val inTrip = screen == Screen.WRAP || screen == Screen.DRIVING
                    LaunchedEffect(inTrip) { if (!inTrip) c.hideEmergency() }
                    Fading(if (inTrip) emergencyUi else null) { em ->
                        IviPopup(
                            onDismiss = c::hideEmergency,
                            buttons = { IviButton("닫기", c::hideEmergency, Modifier.weight(1f), filled = true, size = ButtonSize.LARGE) },
                        ) {
                            if (em.contacts.isNotEmpty()) {
                                PopupTitle("비상연락처", "차주님이 등록한 연락처입니다. 번호를 확인하면 차주님께 알림이 남습니다.")
                                Spacer(Modifier.height(24.dp))
                                PairColumns(em.contacts.map { it.label to it.dialText }, Type.titleSmall, Type.headlineLarge, gap = 16.dp)
                            } else {
                                PopupTitle("비상연락처", em.message)
                            }
                        }
                    }

                    // 운행을 마친 화면에서만 주차 위치를 수정한다. 화면을 떠나면 닫는다.
                    LaunchedEffect(screen) { if (screen != Screen.CLOSED) editParking = false }
                    Fading(if (editParking && screen == Screen.CLOSED) state.lastTrip else null) { last ->
                        ParkingEditPopup(
                            state.card, last.parking, c.handover,
                            onSave = { c.correctParking(it); editParking = false },
                            onDismiss = { editParking = false },
                        )
                    }

                    Fading(if (confirmUnbind && screen == Screen.SETTINGS) pairing.status else null) { status ->
                        IviPopup(
                            onDismiss = { confirmUnbind = false; c.clearPairing() },
                            buttons = {
                                IviButton("취소", { confirmUnbind = false; c.clearPairing() }, Modifier.weight(1f), size = ButtonSize.LARGE)
                                IviButton("연결 해제", c::unbind, Modifier.weight(1f), filled = true, size = ButtonSize.LARGE, loading = busy)
                            },
                        ) {
                            PopupTitle(
                                "번호 연결을 해제할까요?",
                                status.ifBlank { "번호, 예약, 휴대폰 연결 정보가 삭제됩니다. 차에 저장된 이용 내역은 남습니다." },
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 순정 내비게이션 화면으로 돌아간다. 지도 앱을 앞으로 부르고, 부를 수 없는 차에서는
     * 이 앱을 뒤로 보내 바로 앞에 보던 화면(내비게이션)이 다시 보이게 한다.
     */
    private fun backToMap() {
        try {
            startActivity(
                android.content.Intent.makeMainSelectorActivity(android.content.Intent.ACTION_MAIN, android.content.Intent.CATEGORY_APP_MAPS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "map app not found, moving to back", e)
            moveTaskToBack(true)
        }
    }
}
