package com.lrlr.codaeri.domain

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
    const val ASK = "어디로 갈까요?"
    const val EXAMPLE = "예: \"집까지 대리 불러 줘\", \"강남역\""

    private val known = listOf(
        Place("판교 삼평동", 37.4020, 127.1086) to listOf("판교", "삼평동"),
        Place("강남역", 37.4979, 127.0276) to listOf("강남"),
        Place("잠실 롯데타워", 37.5126, 127.1025) to listOf("잠실", "롯데타워", "롯데월드타워"),
    )
    /** "장소 선택"에서 고를 수 있는 곳. 시연에서는 몇 곳만 둔다(실제 서비스에서는 주소 검색이 붙는다). */
    val places: List<Place> get() = known.map { it.first }
    /** 눌러서 고른 뒤 요금 확인 화면에 적는 안내 */
    const val TAP_HINT = "\"부르기\"를 누르면 기사님을 부릅니다. 운행이 끝나면 등록된 결제수단으로 자동 결제됩니다."

    private val homeWords = listOf("집", "우리집", "자택")
    private val noWords = listOf("아니", "취소", "됐어", "안해", "하지마", "그만")
    private val yesWords = listOf("네", "예", "응", "어", "넵", "불러", "맞아", "좋아", "그래", "부탁", "해줘", "오케이", "콜", "그렇게")
    /** 대답을 듣기 전에 화면에 적는 안내. 한 글자 대답("네")은 잘못 알아듣는 일이 있어 긴 말을 권한다. */
    const val ANSWER_HINT = "\"불러 줘\" 또는 \"취소\"라고 말하거나 단추를 눌러 주세요. 운행이 끝나면 등록된 결제수단으로 자동 결제됩니다."
    /** 대답을 알아듣지 못했을 때. 들은 말을 그대로 보여 주고 다시 묻는다(비슷한 말로 짐작해 부르지 않는다). */
    fun retryLine(heard: String?) =
        if (heard.isNullOrBlank()) "대답을 듣지 못했습니다. \"불러 줘\" 또는 \"취소\"라고 말씀해 주세요."
        else "\"$heard\"(으)로 들었습니다. \"불러 줘\" 또는 \"취소\"라고 말씀해 주세요."

    /** 주차 안내에 등록한 집 위치. 위치가 없으면 null */
    fun home(card: ParkingCard): Place? {
        val lat = card.latitude ?: return null
        val lng = card.longitude ?: return null
        return Place(card.placeName.ifBlank { "집" }, lat, lng)
    }

    private fun squeeze(text: String) = text.filterNot { it.isWhitespace() || it in ".,!?~" }

    /** 목적지를 묻고 들은 말 */
    fun destination(text: String, home: Place?): Heard {
        val t = squeeze(text)
        if (t.isEmpty()) return Heard.Unknown
        known.firstOrNull { (place, alias) -> alias.any { it in t } || squeeze(place.name) in t }?.let { return Heard.Destination(it.first) }
        // "집"은 한 글자라 다른 낱말에 섞이지 않게 뒤에 오는 말을 본다
        val saysHome = homeWords.any { w -> t == w || t.startsWith(w + "까지") || t.startsWith(w + "으로") || t.startsWith(w + "에") || t.contains("우리집") || t.startsWith("집대리") }
        if (saysHome) return home?.let { Heard.Destination(it) } ?: Heard.HomeMissing
        return Heard.Unknown
    }

    /** "부를까요?"에 들은 대답. 아니라는 말을 먼저 본다. */
    fun answer(text: String): Heard {
        val t = squeeze(text)
        if (t.isEmpty()) return Heard.Unknown
        if (noWords.any { it in t }) return Heard.No
        if (yesWords.any { t.startsWith(it) || t.endsWith(it) || t.contains("불러") }) return Heard.Yes
        return Heard.Unknown
    }

    fun confirmLine(place: Place, fareText: String) = "${place.name}까지 확정 요금 ${fareText}입니다. 부를까요?"
    fun doneLine(driverName: String) = "$driverName 기사님이 배정됐습니다."
}
