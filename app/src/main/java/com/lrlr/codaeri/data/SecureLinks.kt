package com.lrlr.codaeri.data

import android.util.Base64
import com.lrlr.codaeri.domain.LinkFactory
import com.lrlr.codaeri.domain.PhoneLink
import java.security.SecureRandom

/** 연결 정보와 일회용 코드를 SecureRandom으로 만든다. */
object SecureLinks : LinkFactory {
    private val random = SecureRandom()

    private fun token(bytes: Int): String {
        val b = ByteArray(bytes).also { random.nextBytes(it) }
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /** 추측할 수 없는 주소(128비트)와 차만 아는 쓰기 키(256비트). */
    override fun create(): PhoneLink = PhoneLink(readId = token(16), writeKey = token(32))

    /** 일회용 연결 코드(128비트). 서버에는 해시만 등록한다. */
    override fun pairCode(): String = token(16)

    /**
     * 여섯 글자 연결 코드. QR을 찍을 휴대폰이 없을 때 PC의 심사용 콘솔에 입력한다.
     * 헷갈리는 글자(I, O, 0, 1)는 쓰지 않는다. 일회용 코드와 같은 10분 동안, 한 번만 쓸 수 있다.
     */
    override fun shortCode(): String = (1..6).map { SHORT_CHARS[random.nextInt(SHORT_CHARS.length)] }.joinToString("")

    private const val SHORT_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun sha256(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
