package io.github.ckdgus6068.jellycalendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ckdgus6068.jellycalendar.core.NextAlarm
import io.github.ckdgus6068.jellycalendar.core.Settings
import io.github.ckdgus6068.jellycalendar.ui.alarmSourceName
import io.github.ckdgus6068.jellycalendar.ui.common.JellyButton
import io.github.ckdgus6068.jellycalendar.ui.common.JellyChip
import io.github.ckdgus6068.jellycalendar.ui.common.SectionTitle
import io.github.ckdgus6068.jellycalendar.ui.common.SwitchRow
import io.github.ckdgus6068.jellycalendar.ui.common.ThinDivider
import io.github.ckdgus6068.jellycalendar.ui.hm
import io.github.ckdgus6068.jellycalendar.ui.shortDate
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors

@Composable
fun SettingsScreen(
    settings: Settings,
    nextAlarm: NextAlarm?,
    trayCount: Int,
    onBack: () -> Unit,
    onChange: ((Settings) -> Settings) -> Unit,
    onOpenAlarms: () -> Unit,
    onClearTray: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val colors = LocalJellyColors.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("설정", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SectionTitle("기상 알람 연동")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.wake.copy(alpha = 0.12f))
                    .padding(14.dp),
            ) {
                Text("다음 알람", color = colors.textSub, fontSize = 12.sp)
                Text(
                    text = nextAlarm?.let {
                        "${shortDate(it.date)} ${hm(it.minute)} · ${alarmSourceName(it.source)}"
                    } ?: "예약된 알람이 없어요",
                    color = colors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "휴대폰이 알려 주는 ‘다음 알람’ 하나만 읽을 수 있어요. 기상 시간대 밖의 알람은 무시해요.",
                    color = colors.textSub,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            SwitchRow(
                title = "알람에 맞춰 첫 일과 옮기기",
                description = "‘기상 연동’ 반복 젤리를 알람이 울린 뒤로 자동 배치해요.",
                checked = settings.followAlarm,
                onChange = { on -> onChange { it.copy(followAlarm = on) } },
            )
            Text("알람과 첫 일과 사이 · ${settings.wakeGapMin}분", color = colors.text, fontSize = 15.sp)
            ChipRow(listOf(0, 5, 10, 15, 20, 30, 45, 60), settings.wakeGapMin, { "${it}분" }) { v ->
                onChange { it.copy(wakeGapMin = v) }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "기상 시간대 · ${hm(settings.wakeWindowStartMin)}–${hm(settings.wakeWindowEndMin)}",
                color = colors.text,
                fontSize = 15.sp,
            )
            ChipRow(listOf(2, 3, 4, 5), settings.wakeWindowStartMin / 60, { "${it}시부터" }) { v ->
                onChange { it.copy(wakeWindowStartMin = v * 60) }
            }
            ChipRow(listOf(10, 11, 12, 13, 14), settings.wakeWindowEndMin / 60, { "${it}시까지" }) { v ->
                onChange { it.copy(wakeWindowEndMin = v * 60) }
            }
            SwitchRow(
                title = "알람을 바로 추가",
                description = "끄면 삼성 시계 화면이 열리고, 직접 확인한 뒤 저장해요.",
                checked = settings.alarmSkipUi,
                onChange = { on -> onChange { it.copy(alarmSkipUi = on) } },
            )
            JellyButton(
                "삼성 시계 알람 목록 열기",
                onClick = onOpenAlarms,
                filled = false,
                color = colors.text,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "안드로이드 공개 기능으로는 이미 있는 알람을 지우거나 바꿀 수 없어요. 예전 알람은 시계 앱에서 꺼 주세요.",
                color = colors.textSub,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(8.dp))
            ThinDivider()
            SectionTitle("젤리")
            SwitchRow(
                title = "두 번 톡 해서 완료",
                checked = settings.doneByDoubleTap,
                onChange = { on -> onChange { it.copy(doneByDoubleTap = on) } },
            )
            SwitchRow(
                title = "꾹 눌렀다 떼서 완료",
                description = "꾹 누른 채 움직이면 옮기기가 돼요.",
                checked = settings.doneByLongPress,
                onChange = { on -> onChange { it.copy(doneByLongPress = on) } },
            )
            SwitchRow(
                title = "말랑말랑 숨쉬기",
                description = "가만히 있는 젤리도 살짝 출렁여요. 끄면 배터리를 조금 아껴요.",
                checked = settings.idleWobble,
                onChange = { on -> onChange { it.copy(idleWobble = on) } },
            )
            Text("끌어 놓을 때 맞추는 간격 · ${settings.snapMin}분", color = colors.text, fontSize = 15.sp)
            ChipRow(listOf(5, 10, 15, 30), settings.snapMin, { "${it}분" }) { v -> onChange { it.copy(snapMin = v) } }
            Spacer(Modifier.height(12.dp))
            Text("한 주의 시작", color = colors.text, fontSize = 15.sp)
            ChipRow(listOf(false, true), settings.weekStartsOnSunday, { if (it) "일요일" else "월요일" }) { v ->
                onChange { it.copy(weekStartsOnSunday = v) }
            }

            Spacer(Modifier.height(8.dp))
            ThinDivider()
            SectionTitle("데이터")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                JellyButton("백업 파일 저장", onClick = onExport, filled = false, color = colors.text, modifier = Modifier.weight(1f))
                JellyButton("백업 불러오기", onClick = onImport, filled = false, color = colors.text, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            JellyButton(
                "보관함 비우기 ($trayCount)",
                onClick = onClearTray,
                filled = false,
                color = colors.danger,
                enabled = trayCount > 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (option in options) {
            JellyChip(label(option), selected = option == selected, onClick = { onPick(option) })
        }
    }
}
