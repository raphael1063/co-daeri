package com.lrlr.codaeri.domain

import com.lrlr.codaeri.R

/** 말로 부를 수 있는 목적지. 시연에서는 집과 몇 곳만 알아듣는다(실제 서비스에서는 주소 검색이 붙는다). */
data class Place(val name: String, val lat: Double, val lng: Double)

/** 들은 말을 어떻게 알아들었는지 */
sealed interface Heard {
    data class Destination(val place: Place) : Heard
    /** "집"이라고 했지만 집 위치를 등록하지 않았다 */
    data object HomeMissing : Heard
    data object Yes : Heard
    data object No : Heard
    data object Unknown : Heard
}

/** 차 안에서 대리 부르기에 쓰는 말과 장소. 목소리로 부를 때는 누른 뒤 한 번 듣고, 요금을 확인받은 뒤에만 부른다. */
object VoiceCall {
    val ASK: String get() = text(R.string.voice_ask)
    val EXAMPLE: String get() = text(R.string.voice_example)

    /** 어휘 묶음은 값 하나에 | 로 이어 적었다. 나눠서 쓴다. */
    private fun words(id: Int): List<String> = text(id).split('|')

    private val known: List<Pair<Place, List<String>>> get() = listOf(
        Place(text(R.string.voice_place_pangyo), 37.4020, 127.1086) to words(R.string.voice_alias_pangyo),
        Place(text(R.string.voice_place_gangnam), 37.4979, 127.0276) to words(R.string.voice_alias_gangnam),
        Place(text(R.string.voice_place_jamsil), 37.5126, 127.1025) to words(R.string.voice_alias_jamsil),
    )
    /** "장소 선택"에서 고를 수 있는 곳. 시연에서는 몇 곳만 둔다(실제 서비스에서는 주소 검색이 붙는다). */
    val places: List<Place> get() = known.map { it.first }
    /** 눌러서 고른 뒤 요금 확인 화면에 적는 안내 */
    val TAP_HINT: String get() = text(R.string.voice_tap_hint)

    private val homeWords: List<String> get() = words(R.string.voice_home_words)
    private val noWords: List<String> get() = words(R.string.voice_no_words)
    private val yesWords: List<String> get() = words(R.string.voice_yes_words)
    /** 집 낱말 뒤에 오면 집으로 보는 말("까지", "으로", "에") */
    private val homeSuffixes: List<String> get() = words(R.string.voice_home_suffixes)
    private val homeContains: String get() = text(R.string.voice_home_contains)
    private val homeCallPrefix: String get() = text(R.string.voice_home_call_prefix)
    private val yesContains: String get() = text(R.string.voice_yes_contains)
    /** 대답을 듣기 전에 화면에 적는 안내. 한 글자 대답("네")은 잘못 알아듣는 일이 있어 긴 말을 권한다. */
    val ANSWER_HINT: String get() = text(R.string.voice_answer_hint)
    /** 대답을 알아듣지 못했을 때. 들은 말을 그대로 보여 주고 다시 묻는다(비슷한 말로 짐작해 부르지 않는다). */
    fun retryLine(heard: String?) =
        if (heard.isNullOrBlank()) text(R.string.voice_retry_empty)
        else text(R.string.voice_retry_heard, heard)

    /** 주차 안내에 등록한 집 위치. 위치가 없으면 null */
    fun home(card: ParkingCard): Place? {
        val lat = card.latitude ?: return null
        val lng = card.longitude ?: return null
        return Place(card.placeName.ifBlank { text(R.string.voice_home) }, lat, lng)
    }

    private fun squeeze(text: String) = text.filterNot { it.isWhitespace() || it in ".,!?~" }

    /** 목적지를 묻고 들은 말 */
    fun destination(text: String, home: Place?): Heard {
        val t = squeeze(text)
        if (t.isEmpty()) return Heard.Unknown
        known.firstOrNull { (place, alias) -> alias.any { it in t } || squeeze(place.name) in t }?.let { return Heard.Destination(it.first) }
        // "집"은 한 글자라 다른 낱말에 섞이지 않게 뒤에 오는 말을 본다
        val suffixes = homeSuffixes
        val contains = homeContains
        val callPrefix = homeCallPrefix
        val saysHome = homeWords.any { w -> t == w || suffixes.any { t.startsWith(w + it) } || t.contains(contains) || t.startsWith(callPrefix) }
        if (saysHome) return home?.let { Heard.Destination(it) } ?: Heard.HomeMissing
        return Heard.Unknown
    }

    /** "부를까요?"에 들은 대답. 아니라는 말을 먼저 본다. */
    fun answer(text: String): Heard {
        val t = squeeze(text)
        if (t.isEmpty()) return Heard.Unknown
        if (noWords.any { it in t }) return Heard.No
        val yesAnywhere = yesContains
        if (yesWords.any { t.startsWith(it) || t.endsWith(it) || t.contains(yesAnywhere) }) return Heard.Yes
        return Heard.Unknown
    }

    /** 출발지 이름. 시연에서는 아는 장소에서 1.5km 안이면 그 이름을 붙이고, 아니면 "현재 위치"라고만 적는다(실제 서비스에서는 주소가 붙는다). */
    fun originName(lat: Double?, lng: Double?, home: Place?): String {
        if (lat == null || lng == null) return HERE
        val near = (listOfNotNull(home) + places).minByOrNull { straightKm(lat, lng, it.lat, it.lng) } ?: return HERE
        return if (straightKm(lat, lng, near.lat, near.lng) <= 1.5f) text(R.string.voice_here_near, HERE, near.name) else HERE
    }
    val HERE: String get() = text(R.string.voice_here)

    /** 두 지점 사이의 직선 거리(km). 시연용 요금도 이 거리로 계산한다. */
    fun straightKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val rad = Math.PI / 180
        val dLat = (lat2 - lat1) * rad
        val dLng = (lng2 - lng1) * rad
        val h = Math.sin(dLat / 2).let { it * it } + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2).let { it * it }
        return (12742 * Math.asin(Math.sqrt(h))).toFloat()
    }

    /** 부르기 전에 확인받는 말: 출발지, 목적지, 거리, 요금을 모두 읽는다. */
    fun confirmLine(from: String, place: Place, distanceText: String?, fareText: String) =
        text(R.string.voice_confirm_line, from.substringBefore(" ("), place.name, distanceText?.let { text(R.string.voice_confirm_distance, it) } ?: "", fareText)
    val CONFIRM_TITLE: String get() = text(R.string.voice_confirm_title)
    fun doneLine(driverName: String) = text(R.string.voice_done_line, driverName)
}
