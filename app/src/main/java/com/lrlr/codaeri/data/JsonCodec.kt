package com.lrlr.codaeri.data

import com.lrlr.codaeri.domain.AppState
import com.lrlr.codaeri.domain.CarReport
import com.lrlr.codaeri.domain.EmergencyContact
import com.lrlr.codaeri.domain.HomecomingCard
import com.lrlr.codaeri.domain.ServerState
import kotlinx.serialization.json.Json as KJson

/** 저장 파일과 서버 통신에 쓰는 JSON 변환. 도메인은 이 형식을 모른다. */
object Json {
    private val json = KJson { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(state: AppState): String = json.encodeToString(AppState.serializer(), state)
    fun decodeState(text: String): AppState = json.decodeFromString(AppState.serializer(), text)
    fun encode(card: HomecomingCard): String = json.encodeToString(HomecomingCard.serializer(), card)
    fun encode(report: CarReport): String = json.encodeToString(CarReport.serializer(), report)
    fun decodeConfirm(text: String): Long? = try {
        (json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject)?.get("confirmedAt")
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() }
    } catch (e: Exception) { null }
    fun decodeFare(text: String): Int? = try {
        (json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject)?.get("fare")
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }
    } catch (e: Exception) { null }
    /** 비상연락처 목록. 서버가 하나만 주면(contacts 없음) 그 하나를 목록으로 읽는다. 읽을 것이 없으면 null. */
    fun decodeContacts(text: String): List<EmergencyContact>? = try {
        val o = json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
        val items = (o?.get("contacts") as? kotlinx.serialization.json.JsonArray)?.map { it as? kotlinx.serialization.json.JsonObject } ?: listOf(o)
        items.mapNotNull(::contact).take(com.lrlr.codaeri.domain.Emergency.MAX).ifEmpty { null }
    } catch (e: Exception) { null }
    private fun contact(o: kotlinx.serialization.json.JsonObject?): EmergencyContact? {
        val label = (o?.get("label") as? kotlinx.serialization.json.JsonPrimitive)?.content
        val phone = (o?.get("phone") as? kotlinx.serialization.json.JsonPrimitive)?.content
        return if (label != null && !phone.isNullOrBlank()) EmergencyContact(label, phone) else null
    }
    fun decodeServer(text: String): ServerState = json.decodeFromString(ServerState.serializer(), text)
}
