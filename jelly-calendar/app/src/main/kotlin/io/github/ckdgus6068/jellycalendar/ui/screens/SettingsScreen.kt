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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ckdgus6068.jellycalendar.core.FontChoice
import io.github.ckdgus6068.jellycalendar.core.GoldenFind
import io.github.ckdgus6068.jellycalendar.core.NextAlarm
import io.github.ckdgus6068.jellycalendar.core.Settings
import io.github.ckdgus6068.jellycalendar.ui.alarmSourceName
import io.github.ckdgus6068.jellycalendar.ui.common.JellyButton
import io.github.ckdgus6068.jellycalendar.ui.common.JellyChip
import io.github.ckdgus6068.jellycalendar.ui.common.SectionTitle
import io.github.ckdgus6068.jellycalendar.ui.common.SwitchRow
import io.github.ckdgus6068.jellycalendar.ui.common.ThinDivider
import io.github.ckdgus6068.jellycalendar.ui.hm
import io.github.ckdgus6068.jellycalendar.ui.keepWords
import io.github.ckdgus6068.jellycalendar.ui.shortDate
import io.github.ckdgus6068.jellycalendar.ui.theme.BundledFonts
import io.github.ckdgus6068.jellycalendar.ui.theme.FontChoices
import io.github.ckdgus6068.jellycalendar.ui.theme.fontName
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import io.github.ckdgus6068.jellycalendar.ui.theme.jellyType

@Composable
fun SettingsScreen(
    settings: Settings,
    nextAlarm: NextAlarm?,
    trayCount: Int,
    onBack: () -> Unit,
    onChange: ((Settings) -> Settings) -> Unit,
    onOpenGuide: () -> Unit,
    fonts: BundledFonts,
    appVersion: String,
    /** The golden jelly, once found on this phone: its code can be looked at again here. */
    golden: GoldenFind? = null,
    onOpenGolden: () -> Unit = {},
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
                    keepWords("휴대폰이 알려 주는 ‘다음 알람’ 하나만 읽을 수 있어요. 기상 시간대 밖의 알람은 무시해요."),
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
            SwitchRow(
                title = "새 알람을 맞추면 예전 알람 끄기",
                description = "새 알람을 먼저 추가한 뒤, 바뀌기 전 알람을 끄라고 삼성 시계에 요청해요. 반복 알람은 그날 한 번만 건너뛰어요.",
                checked = settings.autoDismissOld,
                onChange = { on -> onChange { it.copy(autoDismissOld = on) } },
            )
            JellyButton(
                "삼성 시계 알람 목록 열기",
                onClick = onOpenAlarms,
                filled = false,
                color = colors.text,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                keepWords(
                    "알람을 지우거나 시각을 고치는 공개 기능은 없어서, 새 알람을 추가하고 예전 알람을 끄는 방식으로 옮겨요. " +
                        "기기가 끄기 요청을 지원하지 않으면 시계 앱에서 직접 꺼 주세요.",
                ),
                color = colors.textSub,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(8.dp))
            ThinDivider()
            SectionTitle("젤리")
            SwitchRow(
                title = "두 번 톡 하면 다 먹었어요",
                checked = settings.doneByDoubleTap,
                onChange = { on -> onChange { it.copy(doneByDoubleTap = on) } },
            )
            SwitchRow(
                title = "꾹 눌렀다 떼면 다 먹었어요",
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
            SectionTitle("화면")
            Text("글씨체", color = colors.text, fontSize = 15.sp)
            ChipRow(
                options = FontChoices,
                selected = settings.font,
                label = { fontName(it) },
                family = { jellyType(it, fonts).display },
            ) { v -> onChange { it.copy(font = v) } }
            Text(
                keepWords("젤리 이름과 제목에 쓰는 글씨예요. 나머지는 읽기 편한 글씨로 써요. 휴대폰 글꼴은 휴대폰 설정을 따라요."),
                color = colors.textSub,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
            JellyButton(
                "사용법 보기",
                onClick = onOpenGuide,
                filled = false,
                color = colors.text,
                modifier = Modifier.fillMaxWidth(),
            )

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
            if (golden != null) {
                Spacer(Modifier.height(16.dp))
                JellyButton(
                    "🏆 황금 젤리 코드 보기",
                    onClick = onOpenGolden,
                    filled = false,
                    color = Color(0xFFD99A00),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (appVersion.isNotEmpty()) {
                Text(
                    "젤리 캘린더 $appVersion",
                    color = colors.textSub,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    family: ((T) -> FontFamily)? = null,
    onPick: (T) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (option in options) {
            JellyChip(
                label(option),
                selected = option == selected,
                onClick = { onPick(option) },
                fontFamily = family?.invoke(option),
            )
        }
    }
}
