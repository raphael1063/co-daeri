package com.lrlr.codaeri.data.remote

import android.util.Log
import com.lrlr.codaeri.data.Json
import com.lrlr.codaeri.data.SecureLinks
import com.lrlr.codaeri.domain.CarReport
import com.lrlr.codaeri.domain.DaeriService
import com.lrlr.codaeri.domain.EmergencyContact
import com.lrlr.codaeri.domain.HomecomingCard
import com.lrlr.codaeri.domain.PhoneLink
import com.lrlr.codaeri.domain.Place
import com.lrlr.codaeri.domain.ServerState
import java.net.HttpURLConnection
import java.net.URL

/**
 * 대리운전 서비스(시연용 서버)와 HTTP로 통신한다. 차주의 휴대폰 페이지가 올린 귀가 카드를 읽는다.
 * 올리는 내용은 HomecomingCard뿐이다(집 주소, 동·호수, 진입 방법 제외).
 */
class HttpDaeriService(private val server: String) : DaeriService {

    /** 성공하면 결과, 네트워크 오류는 null(나중에 다시 올린다). */
    override fun upload(link: PhoneLink, card: HomecomingCard): DaeriService.Uploaded? {
        var answer: String? = null
        val ok = call("PUT", "", link, Json.encode(card)) { answer = it }
        return if (ok) DaeriService.Uploaded(answer?.let(Json::decodeConfirm)) else null
    }

    /** 일회용 연결 코드의 해시를 등록한다(10분 유효). */
    override fun registerPair(link: PhoneLink, code: String, short: String): Boolean =
        call("POST", "/pair", link, "{\"codeHash\":\"${SecureLinks.sha256(code)}\",\"short\":\"$short\"}")

    /**
     * 묶인 번호, 잡힌 예약, 단골 기사를 서버에 묻는다. 통신이 안 되면 null(다음에 다시 묻는다).
     * 예약은 대리운전 서비스가 번호로 잡은 것이고, 차는 시동 뒤 이것을 물어 인계 화면을 띄운다.
     */
    override fun fetchState(link: PhoneLink, report: CarReport?): ServerState? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$server/api/daeri/cards/${link.readId}/state").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Authorization", "Bearer ${link.writeKey}")
                // 시연용 서버가 예약·확정 시각을 차의 시계에 맞추도록 차의 현재 시각을 함께 보낸다
                setRequestProperty("X-Daeri-Now", System.currentTimeMillis().toString())
                // 지금의 차 신호(운전석 착석, 주행 여부, 위치). 서비스가 이것으로 운행을 관리한다.
                if (report != null) setRequestProperty("X-Daeri-Car", Json.encode(report))
            }
            if (conn.responseCode !in 200..299) null
            else Json.decodeServer(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (e: Exception) {
            Log.w("DaeriUpload", "state failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 차 안에서 부르는 호출. confirm이 false면 요금만 묻고(견적), true면 그 목적지로 예약을 만든다.
     * 돌려주는 값: 견적이면 요금, 예약이면 0 이상(성공). 실패는 null.
     */
    override fun call(link: PhoneLink, place: Place, fromLat: Double?, fromLng: Double?, confirm: Boolean): Int? {
        val body = kotlinx.serialization.json.buildJsonObject {
            put("destination", kotlinx.serialization.json.JsonPrimitive(place.name))
            put("lat", kotlinx.serialization.json.JsonPrimitive(place.lat))
            put("lng", kotlinx.serialization.json.JsonPrimitive(place.lng))
            if (fromLat != null && fromLng != null) {
                put("fromLat", kotlinx.serialization.json.JsonPrimitive(fromLat))
                put("fromLng", kotlinx.serialization.json.JsonPrimitive(fromLng))
            }
            put("confirm", kotlinx.serialization.json.JsonPrimitive(confirm))
        }.toString()
        var answer: String? = null
        if (!call("POST", "/call", link, body) { answer = it }) return null
        return if (confirm) 0 else answer?.let(Json::decodeFare)
    }

    /**
     * 운행 중 기사님이 차 화면에서 비상연락처를 확인한다. 서버는 운행 중인 예약일 때만 번호를 주고,
     * 확인한 사실을 차주 알림에 남긴다. 실패는 null.
     */
    override fun viewEmergency(link: PhoneLink, bookingId: String): List<EmergencyContact>? {
        var answer: String? = null
        val body = kotlinx.serialization.json.buildJsonObject { put("bookingId", kotlinx.serialization.json.JsonPrimitive(bookingId)) }.toString()
        if (!call("POST", "/emergency-view", link, body) { answer = it }) return null
        return answer?.let(Json::decodeContacts)
    }

    /** 운행 상태 자동 공유 켜고 끄기. 서버가 돌려준 새 상태를 읽는다. */
    override fun setShareAuto(link: PhoneLink, on: Boolean): ServerState? {
        var answer: String? = null
        val body = kotlinx.serialization.json.buildJsonObject { put("auto", kotlinx.serialization.json.JsonPrimitive(on)) }.toString()
        if (!call("POST", "/share", link, body) { answer = it }) return null
        return try { answer?.let(Json::decodeServer) } catch (e: Exception) { null }
    }

    override fun cancelBooking(link: PhoneLink): Boolean = call("DELETE", "/ride", link, null)

    override fun addContact(link: PhoneLink, kind: com.lrlr.codaeri.domain.ContactKind, label: String, phone: String): ServerState? {
        var answer: String? = null
        val body = kotlinx.serialization.json.buildJsonObject {
            put("label", kotlinx.serialization.json.JsonPrimitive(label))
            put("phone", kotlinx.serialization.json.JsonPrimitive(phone))
        }.toString()
        if (!call("POST", contactPath(kind), link, body) { answer = it }) return null
        return try { answer?.let(Json::decodeServer) } catch (e: Exception) { null }
    }

    override fun removeContact(link: PhoneLink, kind: com.lrlr.codaeri.domain.ContactKind, index: Int): ServerState? {
        var answer: String? = null
        val body = kotlinx.serialization.json.buildJsonObject { put("index", kotlinx.serialization.json.JsonPrimitive(index)) }.toString()
        if (!call("DELETE", contactPath(kind), link, body) { answer = it }) return null
        return try { answer?.let(Json::decodeServer) } catch (e: Exception) { null }
    }

    private fun contactPath(kind: com.lrlr.codaeri.domain.ContactKind) =
        if (kind == com.lrlr.codaeri.domain.ContactKind.EMERGENCY) "/emergency" else "/share"

    /** 연결한 휴대폰 브라우저를 모두 끊는다. */
    override fun unlinkAll(link: PhoneLink): Boolean = call("DELETE", "/readers", link, null)

    private fun call(method: String, suffix: String, link: PhoneLink, body: String?, onBody: ((String) -> Unit)? = null): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$server/api/daeri/cards/${link.readId}$suffix").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Authorization", "Bearer ${link.writeKey}")
                // 시연용 서버가 예약·확정 시각을 차의 시계에 맞추도록 차의 현재 시각을 함께 보낸다
                setRequestProperty("X-Daeri-Now", System.currentTimeMillis().toString())
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) Log.w("DaeriUpload", "$method$suffix rejected: $code")
            else if (onBody != null) onBody(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            code in 200..299
        } catch (e: Exception) {
            Log.w("DaeriUpload", "$method$suffix failed: ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }
}
