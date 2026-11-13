package com.lrlr.codaeri.ui.screens

import com.lrlr.codaeri.R
import com.lrlr.codaeri.domain.text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lrlr.codaeri.domain.ContactKind
import com.lrlr.codaeri.domain.Emergency
import com.lrlr.codaeri.ui.*

private val RELATIONS get() = listOf(text(R.string.contacts_relation_spouse), text(R.string.contacts_relation_parents), text(R.string.contacts_relation_child), text(R.string.contacts_relation_siblings), text(R.string.contacts_relation_friend), text(R.string.contacts_relation_coworker))
/** 직접 적는 관계의 길이(서비스가 받는 길이와 같다) */
private const val LABEL_MAX = 12
private val KEYS get() = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", text(R.string.shared_erase))

/** 010-1234-5678 꼴로 끊어 적는다(다 치기 전에도). */
internal fun dialing(digits: String): String = when {
    digits.length <= 3 -> digits
    digits.length <= 7 -> digits.take(3) + "-" + digits.drop(3)
    else -> digits.take(3) + "-" + digits.substring(3, 7) + "-" + digits.drop(7)
}

/**
 * 설정에서 여는 연락처 고치기: 비상연락처, 운행 상태를 공유받을 연락처.
 * 저장한 번호는 서비스에만 두므로 이 화면에도 가린 번호로만 보인다. 바꾸려면 지우고 다시 넣는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactsScreen(
    kind: ContactKind,
    contacts: List<Emergency>,
    busy: Boolean,
    message: String,
    onAdd: (label: String, phone: String) -> Unit,
    onRemove: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var digits by rememberSaveable { mutableStateOf("010") }
    // 저장되어 목록이 늘면 입력 칸을 비운다.
    // 직접 입력: 고를 수 있는 관계에 없으면 글자로 적는다
    var custom by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(contacts.size) { label = ""; digits = "010"; custom = false }
    val gap = Arrangement.spacedBy(12.dp)
    val c = Ivi.colors
    // 제목 줄은 위에 고정하고 그 아래만 스크롤한다.
    Column(page) {
        TopBar(if (kind == ContactKind.EMERGENCY) text(R.string.main_emergency_title) else text(R.string.contacts_title_share), onBack, Modifier.appear(0)) { Tag(DEMO_TAG) }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        T(
            if (kind == ContactKind.EMERGENCY) text(R.string.contacts_desc_emergency)
            else text(R.string.contacts_desc_share),
            Type.labelLarge, color = c.textTertiary,
        )
        Spacer(Modifier.height(24.dp))
        Panel(Modifier.fillMaxWidth().appear(1)) {
            if (contacts.isEmpty()) T(text(R.string.contacts_empty), Type.titleSmall, color = c.textSecondary)
            contacts.forEachIndexed { i, contact ->
                if (i > 0) { Spacer(Modifier.height(12.dp)); Divider(); Spacer(Modifier.height(12.dp)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T(contact.label, Type.titleSmall, Modifier.width(FACT_LABEL))
                    T(contact.masked, Type.titleSmall, Modifier.weight(1f))
                    IviButton(text(R.string.shared_delete), { onRemove(i) }, size = ButtonSize.SMALL)
                }
            }
        }
        Reveal(message.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            T(message, Type.labelLarge, color = c.negative)
        }
        Reveal(contacts.size < Emergency.MAX) {
            Spacer(Modifier.height(20.dp))
            Panel(Modifier.fillMaxWidth().appear(2)) {
                T(text(R.string.contacts_add_title), Type.labelLarge, color = c.textTertiary)
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                    RELATIONS.forEach { r -> Choice(r, !custom && label == r, { label = if (!custom && label == r) "" else r; custom = false }) }
                    Choice(text(R.string.contacts_custom_relation), custom, { custom = !custom; label = "" })
                }
                Reveal(custom) {
                    Spacer(Modifier.height(16.dp))
                    Field(text(R.string.contacts_relation_field), label, Modifier.width(640.dp)) { label = it.take(LABEL_MAX) }
                }
                Spacer(Modifier.height(24.dp))
                T(dialing(digits).ifEmpty { text(R.string.contacts_phone_placeholder) }, Type.headlineSmall, color = if (digits.isEmpty()) c.textTertiary else c.textPrimary)
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                    KEYS.forEach { k -> Choice(k, false, { digits = if (k == text(R.string.shared_erase)) digits.dropLast(1) else (digits + k).take(11) }) }
                }
                Spacer(Modifier.height(24.dp))
                IviButton(
                    text(R.string.contacts_add), { onAdd(label.trim(), digits) }, Modifier.fillMaxWidth(), filled = true, size = ButtonSize.LARGE,
                    enabled = label.isNotBlank() && digits.length >= 9, loading = busy,
                )
                Spacer(Modifier.height(12.dp))
                T(text(R.string.contacts_masked_note), Type.labelSmall, color = c.textTertiary)
            }
        }
    }
    }
}
