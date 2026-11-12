package com.lrlr.codaeri.data.local

import android.content.Context
import android.util.Log
import com.lrlr.codaeri.data.Json
import com.lrlr.codaeri.domain.AppState
import com.lrlr.codaeri.domain.StateRepository
import java.io.File

/** 차 안 저장소. 앱 전용 폴더의 JSON 파일 하나. 쓰기는 임시 파일을 거쳐 통째로 바꾼다. */
class FileStateRepository(context: Context) : StateRepository {
    private val file = File(context.filesDir, "daeri-state.json")
    private val tmp = File(context.filesDir, "daeri-state.json.tmp")

    override fun load(): AppState = try {
        if (file.exists()) Json.decodeState(file.readText()) else AppState()
    } catch (e: Exception) {
        Log.w("DaeriStore", "state unreadable, starting fresh", e)
        AppState()
    }

    @Synchronized
    override fun save(state: AppState) {
        try {
            tmp.writeText(Json.encode(state))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w("DaeriStore", "save failed", e)
        }
    }
}
