package com.lrlr.codaeri.domain

/**
 * 화면, 안내 음성, 알림, 음성 인식 어휘는 모두 res/values/strings.xml 에 키(R.string.*)로 둔다.
 * 도메인은 안드로이드를 모르므로 Int 키만 받고, 이 창구 하나로 문구를 꺼낸다.
 * 앱에서는 DaeriApp이 getString으로 잇고, 단위 시험에서는 TestTexts가 strings.xml을 직접 읽어 잇는다.
 */
object Texts {
    @Volatile
    var resolver: (Int, Array<out Any?>) -> String = { id, _ -> error("문구 창구가 연결되지 않았습니다 (id=$id)") }
}

/** 문구 키로 문장을 꺼낸다. 인자는 %1\$s, %2\$d 자리에 차례로 들어간다. */
fun text(id: Int, vararg args: Any?): String = Texts.resolver(id, args)
