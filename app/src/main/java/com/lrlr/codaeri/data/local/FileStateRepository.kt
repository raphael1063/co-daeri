package com.lrlr.codaeri.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.lrlr.codaeri.data.Json
import com.lrlr.codaeri.domain.AppState
import com.lrlr.codaeri.domain.StateRepository
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 차 안 저장소. 앱 전용 폴더의 파일 하나이고, 서버 열쇠가 들어 있으므로 Android Keystore의 키로 암호화해 둔다.
 * 쓰기는 임시 파일을 거쳐 통째로 바꾼다. 예전 버전이 남긴 평문 파일은 처음 읽을 때 암호화해 옮기고 지운다.
 */
class FileStateRepository(context: Context) : StateRepository {
    private val file = File(context.filesDir, "daeri-state.bin")
    private val tmp = File(context.filesDir, "daeri-state.bin.tmp")
    private val legacy = File(context.filesDir, "daeri-state.json")

    override fun load(): AppState = try {
        when {
            file.exists() -> Json.decodeState(open(file.readBytes()))
            legacy.exists() -> Json.decodeState(legacy.readText()).also { save(it) }
            else -> AppState()
        }
    } catch (e: Exception) {
        Log.w(TAG, "state unreadable, starting fresh", e)
        AppState()
    }

    @Synchronized
    override fun save(state: AppState) {
        try {
            tmp.writeBytes(seal(Json.encode(state)))
            if (!tmp.renameTo(file)) {
                file.writeBytes(tmp.readBytes())
                tmp.delete()
            }
            // 암호화한 파일이 생긴 뒤에만 예전 평문 파일을 지운다.
            if (legacy.exists()) legacy.delete()
        } catch (e: Exception) {
            Log.w(TAG, "save failed", e)
        }
    }

    /** 앞 12바이트는 IV, 나머지는 AES-GCM 암호문 */
    private fun seal(text: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
    }

    private fun open(bytes: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        }
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    /** 키는 Keystore 밖으로 나오지 않는다. 없으면 한 번 만든다. */
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val TAG = "DaeriStore"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "codaeri-state"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
