package com.lrlr.codaeri.domain

/** 한 번에 읽는 안내 한 덩어리. */
data class GuideSegment(
    val steps: List<GuideStep>,
    /** 스피커로 읽는 문장 */
    val speech: String,
    /** 주행 중 화면에 큰 글자로 띄우는 핵심 한 줄 */
    val headline: String,
)

/**
 * 주차 카드를 기본 안내(진입구, 진입 방법)와 상세 안내(구역, 선호 위치, 자리가 없으면)로 나눈다.
 * 위치로 단계를 바꾸지 않는다(지하에서 위치가 끊기므로). 기사가 "다음 안내"를 눌러야 넘어간다.
 */
object Guidance {
    const val MORE_HINT = "주차 위치는 다음 안내를 누르면 알려 드립니다."
    const val END_LINE = "주차 안내는 여기까지입니다."

    fun label(step: GuideStep): String = when (step) {
        GuideStep.ENTRANCE -> "주차장 입구"
        GuideStep.ENTRY_METHOD -> "차단기 통과 방법"
        GuideStep.ZONE -> "주차 위치"
        GuideStep.PREFERRED_SPOT -> "주차 시 유의사항"
        GuideStep.FALLBACK -> "자리가 없을 때"
    }

    fun text(card: ParkingCard, step: GuideStep): String = when (step) {
        GuideStep.ENTRANCE -> card.entrance
        GuideStep.ENTRY_METHOD -> card.entryMethod
        GuideStep.ZONE -> locationWithNote(card.floorLabel, card.zone)
        GuideStep.PREFERRED_SPOT -> locationWithNote(card.spotLabel.takeIf { it.isNotBlank() }?.let { "$it 근처에 주차해 주세요" }.orEmpty(), card.preferredSpot, card.spotLabel)
        GuideStep.FALLBACK -> card.fallback
    }.trim()

    /** 위치는 한 번 입력하고 음성 안내와 이용 내역에 함께 쓴다. 기존의 상세 안내도 보존한다. */
    private fun locationWithNote(location: String, note: String, match: String = location): String = when {
        note.isBlank() -> location
        location.isBlank() || (match.isNotBlank() && note.contains(match)) -> note
        else -> "$location. $note"
    }

    /** 순서대로 읽을 덩어리. 비어 있는 항목은 건너뛴다. 첫 덩어리가 기본 안내다. */
    fun segments(card: ParkingCard): List<GuideSegment> {
        val basicSteps = listOf(GuideStep.ENTRANCE, GuideStep.ENTRY_METHOD)
            .filter { text(card, it).isNotEmpty() }
        // 상세 안내는 두 번에 나눈다: (구역 + 선호 위치), 그다음 (자리가 없으면).
        val detailGroups = listOf(
            listOf(GuideStep.ZONE, GuideStep.PREFERRED_SPOT),
            listOf(GuideStep.FALLBACK),
        ).map { g -> g.filter { text(card, it).isNotEmpty() } }.filter { it.isNotEmpty() }
        val detailSteps = detailGroups.flatten()

        val result = mutableListOf<GuideSegment>()
        val intro = buildString {
            append("코대리 주차 안내입니다. ")
            if (card.placeName.isNotBlank()) append("${card.placeName}에 거의 도착했습니다. ")
        }
        if (basicSteps.isNotEmpty()) {
            val body = basicSteps.joinToString(" ") { sentence(text(card, it)) }
            val tail = if (detailSteps.isNotEmpty()) " $MORE_HINT" else ""
            result += GuideSegment(
                steps = basicSteps,
                speech = intro + body + tail,
                headline = text(card, basicSteps.first()),
            )
        }
        detailGroups.forEachIndexed { index, group ->
            val prefix = if (result.isEmpty() && index == 0) intro else ""
            result += GuideSegment(
                steps = group,
                // 칸 이름은 화면에만 쓴다. 내용이 이미 문장이라 이름까지 읽으면 같은 말이 겹친다.
                speech = prefix + group.joinToString(" ") { sentence(text(card, it)) },
                headline = text(card, group.first()),
            )
        }
        return result
    }

    private fun sentence(s: String): String {
        val t = s.trim()
        return if (t.endsWith(".") || t.endsWith("요") || t.endsWith("다")) t.trimEnd('.') + "." else "$t."
    }
}

/**
 * 안내 진행 상태. 순수 상태라 테스트할 수 있다.
 * position은 마지막으로 읽은 덩어리 번호(-1 = 아직 안 읽음).
 */
data class GuidanceState(
    val segments: List<GuideSegment>,
    val position: Int = -1,
) {
    val current: GuideSegment? get() = segments.getOrNull(position)
    val hasNext: Boolean get() = position + 1 < segments.size
    val started: Boolean get() = position >= 0

    /** 지금까지 읽은 항목 */
    val heard: Set<GuideStep>
        get() = segments.take(position + 1).flatMap { it.steps }.toSet()

    /** 아직 듣지 않은 항목. P단으로 정차하면 화면에 글로 보여 준다. */
    val unheard: List<GuideStep>
        get() = segments.drop(position + 1).flatMap { it.steps }

    fun start(): GuidanceState = if (started || segments.isEmpty()) this else copy(position = 0)
    fun next(): GuidanceState = if (hasNext) copy(position = position + 1) else this
}

