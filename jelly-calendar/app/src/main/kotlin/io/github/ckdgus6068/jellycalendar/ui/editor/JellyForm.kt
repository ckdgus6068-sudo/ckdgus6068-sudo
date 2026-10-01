package io.github.ckdgus6068.jellycalendar.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.ckdgus6068.jellycalendar.core.ALL_DAYS
import io.github.ckdgus6068.jellycalendar.core.Jelly
import io.github.ckdgus6068.jellycalendar.core.JellyStatus
import io.github.ckdgus6068.jellycalendar.core.MINUTES_PER_DAY
import io.github.ckdgus6068.jellycalendar.core.Planner
import io.github.ckdgus6068.jellycalendar.core.Routine
import io.github.ckdgus6068.jellycalendar.core.WEEKDAYS
import io.github.ckdgus6068.jellycalendar.core.WEEKEND
import io.github.ckdgus6068.jellycalendar.ui.common.JellyButton
import io.github.ckdgus6068.jellycalendar.ui.common.JellyChip
import io.github.ckdgus6068.jellycalendar.ui.common.SectionTitle
import io.github.ckdgus6068.jellycalendar.ui.common.SwitchRow
import io.github.ckdgus6068.jellycalendar.ui.common.squishyClick
import io.github.ckdgus6068.jellycalendar.ui.dayName
import io.github.ckdgus6068.jellycalendar.ui.daysText
import io.github.ckdgus6068.jellycalendar.ui.durationText
import io.github.ckdgus6068.jellycalendar.ui.hm
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyBody
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyLabel
import io.github.ckdgus6068.jellycalendar.ui.jelly.rememberJellyMotion
import io.github.ckdgus6068.jellycalendar.ui.keepWords
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyFlavors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import java.time.LocalDate

enum class EditorKind { JELLY, ROUTINE }

/** Everything the editor sheet can change, held while the sheet is open. */
@Stable
class EditorState(
    val kind: EditorKind,
    val jellyId: String?,
    val routineId: String?,
    val status: JellyStatus?,
    val linkedRoutine: Routine?,
    title: String,
    flavor: Int,
    duration: Int,
    date: LocalDate?,
    start: Int?,
    days: Set<Int>,
    wakeAnchored: Boolean,
    carryOver: Boolean,
    note: String,
    active: Boolean,
) {
    var title by mutableStateOf(title)
    var flavor by mutableStateOf(flavor)
    var duration by mutableStateOf(duration)
    var date by mutableStateOf(date)
    var start by mutableStateOf(start)
    var days by mutableStateOf(days)
    var wakeAnchored by mutableStateOf(wakeAnchored)
    var carryOver by mutableStateOf(carryOver)
    var note by mutableStateOf(note)
    var active by mutableStateOf(active)

    val isNew: Boolean get() = if (kind == EditorKind.JELLY) jellyId == null else routineId == null
    val repeats: Boolean get() = kind == EditorKind.ROUTINE || days.isNotEmpty()

    companion object {
        fun newJelly(date: LocalDate?, start: Int?, flavor: Int) = EditorState(
            EditorKind.JELLY, null, null, null, null,
            "", flavor, 50, date, start, emptySet(), false, true, "", true,
        )

        fun editJelly(jelly: Jelly, routine: Routine?) = EditorState(
            EditorKind.JELLY, jelly.id, null, jelly.status, routine,
            jelly.title, jelly.flavor, jelly.durationMin, jelly.date, jelly.startMin,
            emptySet(), jelly.wakeAnchored, jelly.carryOver, jelly.note, true,
        )

        fun newRoutine(flavor: Int, start: Int = 7 * 60) = EditorState(
            EditorKind.ROUTINE, null, null, null, null,
            "", flavor, 50, null, start, ALL_DAYS, false, true, "", true,
        )

        fun editRoutine(routine: Routine) = EditorState(
            EditorKind.ROUTINE, null, routine.id, null, null,
            routine.title, routine.flavor, routine.durationMin, null, routine.startMin,
            routine.days, routine.wakeAnchored, routine.carryOver, routine.note, routine.active,
        )
    }
}

private val DURATIONS = listOf(10, 15, 20, 30, 45, 50, 60, 90, 120)

/** The editor sheet for one jelly or one repeating jelly. */
@Composable
fun JellyForm(
    state: EditorState,
    weekStart: LocalDate,
    gapMin: Int,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onToggleDone: () -> Unit,
    onSendToTray: () -> Unit,
    onOpenRoutine: (Routine) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalJellyColors.current
    var pickingTime by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Text(
            text = when {
                state.kind == EditorKind.ROUTINE && state.isNew -> "새 반복 젤리"
                state.kind == EditorKind.ROUTINE -> "반복 젤리 편집"
                state.isNew -> "새 젤리"
                else -> "젤리 편집"
            },
            color = colors.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))
        PreviewJelly(state)
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = state.title,
            onValueChange = { state.title = it.take(40) },
            label = { Text("이름") },
            placeholder = { Text("예: 아침 러닝") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("색")
        FlavorPicker(state.flavor) { state.flavor = it }

        SectionTitle("길이 · ${durationText(state.duration)}")
        DurationPicker(state.duration) { state.duration = it }

        if (state.kind == EditorKind.JELLY) {
            SectionTitle("언제")
            DatePicker(state, weekStart)
        }
        if (state.kind == EditorKind.ROUTINE || state.date != null) {
            SectionTitle(if (state.kind == EditorKind.ROUTINE) "기본 시작 시간" else "시작 시간")
            TimeRow(state) { pickingTime = true }
        }

        val linked = state.linkedRoutine
        if (state.kind == EditorKind.ROUTINE) {
            SectionTitle("반복 요일 · ${daysText(state.days)}")
            DaysPicker(state.days, allowNone = false) { state.days = it }
        } else if (linked == null) {
            SectionTitle("반복")
            DaysPicker(state.days, allowNone = true) { state.days = it }
        } else {
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceSoft)
                    .padding(14.dp),
            ) {
                Text(
                    keepWords("‘${daysText(linked.days)} ${hm(linked.startMin)} · ${durationText(linked.durationMin)}’ 반복 젤리의 하루치예요."),
                    color = colors.text,
                    fontSize = 13.sp,
                )
                Text("여기서 바꾸면 이 날만 바뀌어요.", color = colors.textSub, fontSize = 12.sp)
                TextButton(onClick = { onOpenRoutine(linked) }) { Text("반복 설정 열기") }
            }
        }

        SectionTitle("옵션")
        SwitchRow(
            title = "못 하면 보관함으로",
            description = "그날 못 한 젤리를 아래 보관함으로 옮겨요. 끄면 그날에 ‘놓침’으로 남아요.",
            checked = state.carryOver,
            onChange = { state.carryOver = it },
        )
        if (state.repeats) {
            SwitchRow(
                title = "기상 알람에 맞춰 시작",
                description = "삼성 시계의 다음 알람이 울린 뒤 ${gapMin}분에 시작하도록 자동으로 옮겨요.",
                checked = state.wakeAnchored,
                onChange = { state.wakeAnchored = it },
            )
        }
        if (state.kind == EditorKind.ROUTINE && !state.isNew) {
            SwitchRow(
                title = "사용",
                description = "끄면 새 날부터 이 젤리를 깔지 않아요.",
                checked = state.active,
                onChange = { state.active = it },
            )
        }

        SectionTitle("메모")
        OutlinedTextField(
            value = state.note,
            onValueChange = { state.note = it.take(500) },
            placeholder = { Text("준비물, 장소 등") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        )

        Spacer(Modifier.height(20.dp))
        if (!state.isNew) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.kind == EditorKind.JELLY) {
                    JellyButton(
                        text = if (state.status == JellyStatus.DONE) "완료 취소" else "완료",
                        onClick = onToggleDone,
                        filled = false,
                        color = colors.ok,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.date != null) {
                        JellyButton(
                            text = "보관함으로",
                            onClick = onSendToTray,
                            filled = false,
                            color = colors.text,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                JellyButton(
                    text = "삭제",
                    onClick = onDelete,
                    filled = false,
                    color = colors.danger,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        JellyButton(
            text = "저장",
            onClick = onSave,
            enabled = state.title.isNotBlank() && (state.kind == EditorKind.JELLY || state.days.isNotEmpty()),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (pickingTime) {
        TimePickDialog(
            initial = state.start ?: 9 * 60,
            onDismiss = { pickingTime = false },
            onPick = {
                state.start = Planner.clampStart(it, state.duration)
                pickingTime = false
            },
        )
    }
}

@Composable
private fun PreviewJelly(state: EditorState) {
    val motion = rememberJellyMotion("editor-preview", state.status == JellyStatus.DONE)
    LaunchedEffect(state.flavor) { motion.kick(1f) }
    LaunchedEffect(state.duration) { motion.kick(0.6f) }
    val flavor = JellyFlavors[state.flavor]
    val heightDp = (18f + state.duration * 0.55f).coerceIn(34f, 118f)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        JellyBody(
            flavor = flavor,
            modifier = Modifier.width(170.dp).height(heightDp.dp),
            motion = motion,
            cornerRadius = 16.dp,
        ) {
            JellyLabel(
                title = state.title.ifBlank { "이름 없는 젤리" },
                subtitle = durationText(state.duration),
                flavor = flavor,
                motion = motion,
                heightDp = heightDp,
                compact = false,
            )
        }
    }
}

@Composable
private fun FlavorPicker(selected: Int, onPick: (Int) -> Unit) {
    val colors = LocalJellyColors.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        JellyFlavors.all.forEachIndexed { index, flavor ->
            val isSelected = index == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(2.dp, if (isSelected) flavor.deep else colors.background, CircleShape)
                        .padding(4.dp),
                ) {
                    JellyBody(
                        flavor = flavor,
                        modifier = Modifier
                            .size(32.dp)
                            .graphicsLayer {
                                scaleX = if (isSelected) 1.05f else 0.9f
                                scaleY = if (isSelected) 1.05f else 0.9f
                            }
                            .squishyClick { onPick(index) },
                        cornerRadius = 12.dp,
                    )
                }
                Text(flavor.name, color = if (isSelected) colors.text else colors.textSub, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun DurationPicker(duration: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JellyChip("−5분", selected = false, onClick = { onChange((duration - 5).coerceAtLeast(Planner.MIN_DURATION)) })
        for (d in DURATIONS) {
            JellyChip(durationText(d), selected = d == duration, onClick = { onChange(d) })
        }
        JellyChip("+5분", selected = false, onClick = { onChange((duration + 5).coerceAtMost(MINUTES_PER_DAY)) })
    }
}

@Composable
private fun DatePicker(state: EditorState, weekStart: LocalDate) {
    var shownWeek by remember { mutableStateOf(weekStart) }
    val colors = LocalJellyColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        JellyChip("‹", selected = false, onClick = { shownWeek = shownWeek.minusDays(7) })
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (i in 0L..6L) {
                val date = shownWeek.plusDays(i)
                JellyChip(
                    text = "${dayName(date)} ${date.dayOfMonth}",
                    selected = state.date == date,
                    onClick = {
                        state.date = date
                        if (state.start == null) state.start = 9 * 60
                    },
                )
            }
        }
        JellyChip("›", selected = false, onClick = { shownWeek = shownWeek.plusDays(7) })
    }
    Spacer(Modifier.height(8.dp))
    JellyChip(
        text = "날짜 없이 보관함에 두기",
        selected = state.date == null,
        onClick = {
            state.date = null
            state.start = null
        },
        accent = colors.textSub,
    )
}

@Composable
private fun TimeRow(state: EditorState, onPick: () -> Unit) {
    val colors = LocalJellyColors.current
    val start = state.start ?: 9 * 60
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JellyChip("−10분", selected = false, onClick = { state.start = Planner.clampStart(start - 10, state.duration) })
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.accent.copy(alpha = 0.12f),
            modifier = Modifier.squishyClick(onClick = onPick),
        ) {
            Text(
                hm(start),
                color = colors.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        JellyChip("+10분", selected = false, onClick = { state.start = Planner.clampStart(start + 10, state.duration) })
        Text("→ ${hm(start + state.duration)}", color = colors.textSub, fontSize = 13.sp)
    }
}

@Composable
private fun DaysPicker(days: Set<Int>, allowNone: Boolean, onChange: (Set<Int>) -> Unit) {
    val colors = LocalJellyColors.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (allowNone) JellyChip("안 함", selected = days.isEmpty(), onClick = { onChange(emptySet()) })
        JellyChip("매일", selected = days == ALL_DAYS, onClick = { onChange(ALL_DAYS) })
        JellyChip("평일", selected = days == WEEKDAYS, onClick = { onChange(WEEKDAYS) })
        JellyChip("주말", selected = days == WEEKEND, onClick = { onChange(WEEKEND) })
    }
    if (days.isNotEmpty() || !allowNone) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (day in 1..7) {
                val on = day in days
                val tint = when (day) {
                    6 -> colors.saturday
                    7 -> colors.sunday
                    else -> colors.accent
                }
                Box(
                    Modifier
                        .size(38.dp)
                        .squishyClick { onChange(if (on) days - day else days + day) }
                        .clip(CircleShape)
                        .background(if (on) tint else colors.surfaceSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        dayName(day),
                        color = if (on) colors.onAccent else colors.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val colors = LocalJellyColors.current
    val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surface) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("시작 시간", color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("확인") }
                }
            }
        }
    }
}
