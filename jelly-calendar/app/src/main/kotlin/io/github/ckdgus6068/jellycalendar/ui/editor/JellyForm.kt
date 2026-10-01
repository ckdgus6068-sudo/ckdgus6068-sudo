package io.github.ckdgus6068.jellycalendar.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.ckdgus6068.jellycalendar.core.ALL_DAYS
import io.github.ckdgus6068.jellycalendar.core.GOLDEN_FLAVOR
import io.github.ckdgus6068.jellycalendar.core.Jelly
import io.github.ckdgus6068.jellycalendar.core.JellyStatus
import io.github.ckdgus6068.jellycalendar.core.KoreanHolidays
import io.github.ckdgus6068.jellycalendar.core.Planner
import io.github.ckdgus6068.jellycalendar.core.Routine
import io.github.ckdgus6068.jellycalendar.core.TitleTime
import io.github.ckdgus6068.jellycalendar.core.WEEKDAYS
import io.github.ckdgus6068.jellycalendar.core.WEEKEND
import io.github.ckdgus6068.jellycalendar.ui.common.AlarmDateDialog
import io.github.ckdgus6068.jellycalendar.ui.common.JellyButton
import io.github.ckdgus6068.jellycalendar.ui.common.JellyChip
import io.github.ckdgus6068.jellycalendar.ui.common.SwitchRow
import io.github.ckdgus6068.jellycalendar.ui.common.clickableNoRipple
import io.github.ckdgus6068.jellycalendar.ui.common.squishyClick
import io.github.ckdgus6068.jellycalendar.ui.dateTitle
import io.github.ckdgus6068.jellycalendar.ui.dayName
import io.github.ckdgus6068.jellycalendar.ui.repeatText
import io.github.ckdgus6068.jellycalendar.ui.shortDate
import io.github.ckdgus6068.jellycalendar.ui.durationText
import io.github.ckdgus6068.jellycalendar.ui.endText
import io.github.ckdgus6068.jellycalendar.ui.hm
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyBody
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyLabel
import io.github.ckdgus6068.jellycalendar.ui.jelly.rememberJellyMotion
import io.github.ckdgus6068.jellycalendar.ui.keepWords
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyFlavor
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyFlavors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

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
    pinned: Boolean = false,
    everyDays: Int = 0,
    cycleStart: LocalDate? = null,
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
    var pinned by mutableStateOf(pinned)

    /** Every this many days instead of on [days] (0 = on [days]); see [Routine.everyDays]. */
    var everyDays by mutableStateOf(everyDays)

    /** A day of the cycle; a jelly's own date is used for a jelly that becomes repeating. */
    var cycleStart by mutableStateOf(cycleStart)

    /** Minutes before the start that a clock alarm is set for. */
    var alarmBefore by mutableStateOf(10)

    /** The time was set by hand: a time written in the title no longer moves it. */
    var timeTouched by mutableStateOf(false)

    /** The words in the title ("11시") that a new jelly's time was taken from. */
    var timeFromTitle by mutableStateOf<String?>(null)
    private var startBeforeTitle: Int? = null

    /** A new jelly or repeating jelly starts when its title says ("11시 미용실"), until its time is set by hand. */
    fun followTitle() {
        if (!isNew || timeTouched || (kind == EditorKind.JELLY && date == null)) return
        val said = TitleTime.find(title)
        if (said != null) {
            if (timeFromTitle == null) startBeforeTitle = start
            start = Planner.clampStart(said.minute, duration)
            timeFromTitle = said.text
        } else if (timeFromTitle != null) {
            start = startBeforeTitle
            timeFromTitle = null
        }
    }

    /** The time set by hand. */
    fun setStartByHand(minute: Int) {
        start = Planner.clampStart(minute, duration)
        timeTouched = true
        timeFromTitle = null
    }

    val isNew: Boolean get() = if (kind == EditorKind.JELLY) jellyId == null else routineId == null
    val repeats: Boolean get() = kind == EditorKind.ROUTINE || willRepeat

    /** A jelly that turns into a repeating one on saving: on days of the week, or every few days. */
    val willRepeat: Boolean get() = days.isNotEmpty() || everyDays >= 2

    /** Something beyond name, colour, length and time is set, so the extras start open. */
    val hasExtras: Boolean get() = pinned || willRepeat || note.isNotBlank() || !carryOver || wakeAnchored

    companion object {
        fun newJelly(date: LocalDate?, start: Int?, flavor: Int) = EditorState(
            EditorKind.JELLY, null, null, null, null,
            "", flavor, 60, date, start, emptySet(), false, true, "", true,
        )

        fun editJelly(jelly: Jelly, routine: Routine?) = EditorState(
            EditorKind.JELLY, jelly.id, null, jelly.status, routine,
            jelly.title, jelly.flavor, jelly.durationMin, jelly.date, jelly.startMin,
            emptySet(), jelly.wakeAnchored, jelly.carryOver, jelly.note, true, jelly.pinned,
        )

        fun newRoutine(flavor: Int, start: Int = 7 * 60) = EditorState(
            EditorKind.ROUTINE, null, null, null, null,
            "", flavor, 50, null, start, ALL_DAYS, false, true, "", true,
        )

        fun editRoutine(routine: Routine) = EditorState(
            EditorKind.ROUTINE, null, routine.id, null, null,
            routine.title, routine.flavor, routine.durationMin, null, routine.startMin,
            routine.days, routine.wakeAnchored, routine.carryOver, routine.note, routine.active,
            everyDays = if (routine.inCycle) routine.everyDays else 0,
            cycleStart = routine.cycleStart,
        )
    }
}

/**
 * The editor sheet for one jelly or one repeating jelly. The essentials come first (name, colour,
 * length, when); pinning, alarms, repeating, the tray rule and a note wait under "더 보기".
 */
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
    now: LocalDateTime = LocalDateTime.now(),
    sundayFirst: Boolean = true,
    canPin: (LocalDate?) -> Boolean = { true },
    /** Asks the clock app for an alarm at [minute] of the coming day; false when nothing took it. */
    onSetAlarm: ((minute: Int, label: String) -> Boolean)? = null,
    /** Opens Samsung Clock's new-alarm screen for an alarm on a later day; false when it did not open. */
    onPickAlarmDate: ((minute: Int, label: String) -> Boolean)? = null,
) {
    val colors = LocalJellyColors.current
    val type = LocalJellyType.current
    var pickingTime by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(state.hasExtras) }
    val isJelly = state.kind == EditorKind.JELLY

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Text(
            text = when {
                !isJelly && state.isNew -> "새 반복 젤리 빚기"
                !isJelly -> "반복 젤리 다듬기"
                state.isNew -> "새 젤리 빚기"
                else -> "젤리 다듬기"
            },
            color = colors.textSub,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        PreviewJelly(state)
        Spacer(Modifier.height(12.dp))

        // The name, written big in the jelly lettering, without a box around it.
        val flavor = JellyFlavors[state.flavor]
        BasicTextField(
            value = state.title,
            onValueChange = {
                state.title = it.take(40)
                state.followTitle()
            },
            singleLine = true,
            cursorBrush = SolidColor(flavor.deep),
            textStyle = TextStyle(color = colors.text, fontSize = 22.sp, fontFamily = type.display, fontWeight = type.displayWeight),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { field ->
                Column {
                    Box {
                        if (state.title.isEmpty()) {
                            Text(
                                if (isJelly) "무엇을 할까요?" else "매번 할 일은?",
                                color = colors.textSub.copy(alpha = 0.6f),
                                fontSize = 22.sp,
                                fontFamily = type.display,
                                fontWeight = type.displayWeight,
                            )
                        }
                        field()
                    }
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(2.dp).background(flavor.base))
                }
            },
        )

        Label("맛")
        FlavorPicker(state.flavor) { state.flavor = it }

        Label("길이 · 쭉 당기면 늘어나요")
        StretchLength(state.duration, flavor) { state.duration = it }

        if (isJelly) {
            Label("언제")
            WhenPicker(state, now.toLocalDate(), sundayFirst)
        }
        if (!isJelly || state.date != null) {
            Label(if (isJelly) "몇 시에" else "보통 몇 시에")
            TimeRow(state) { pickingTime = true }
        }

        val linked = state.linkedRoutine
        if (!isJelly) {
            Label("반복 · ${repeatText(state.days, state.everyDays)}")
            RepeatPicker(state, allowNone = false, today = now.toLocalDate())
        } else if (linked != null) {
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceSoft)
                    .padding(14.dp),
            ) {
                Text(
                    keepWords("‘${repeatText(linked)} ${hm(linked.startMin)} · ${durationText(linked.durationMin)}’ 반복 젤리의 하루치예요."),
                    color = colors.text,
                    fontSize = 13.sp,
                )
                Text("여기서 바꾸면 이 날만 바뀌어요.", color = colors.textSub, fontSize = 12.sp)
                TextButton(onClick = { onOpenRoutine(linked) }) { Text("반복 설정 열기") }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickableNoRipple { more = !more }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (more) "더 보기 접기" else "더 보기",
                color = colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (more) " ▴" else " ▾  젤위로 고정 · 알람 · 반복 · 메모",
                color = colors.textSub,
                fontSize = 12.sp,
            )
        }
        AnimatedVisibility(visible = more) {
            Column {
                if (isJelly) {
                    val dayFull = state.date != null && !state.pinned && !canPin(state.date)
                    val pinnable = state.date != null && state.start != null && !state.willRepeat && !dayFull
                    SwitchRow(
                        title = "📌 젤위로 고정",
                        description = when {
                            state.date == null -> "날짜가 정해진 젤리만 고정할 수 있어요."
                            state.willRepeat -> "반복 젤리는 만든 뒤에 그날 것을 열어서 고정해 주세요."
                            dayFull -> "이 날은 벌써 3개가 고정돼 있어요. 하나를 풀면 고정할 수 있어요."
                            else -> "젤 중요한 젤리를 상자 맨 위에 꼭 붙여 둬요. 하루 3개까지예요."
                        },
                        checked = state.pinned,
                        onChange = { if (!it || pinnable) state.pinned = it },
                    )
                    AlarmRow(state, now, onSetAlarm, onPickAlarmDate)
                    if (linked == null) {
                        Label("반복")
                        RepeatPicker(state, allowNone = true, today = now.toLocalDate())
                    }
                }
                SwitchRow(
                    title = "🧺 못 하면 보관함으로",
                    description = "그날 못 한 젤리를 아래 보관함에 쏙 넣어 둬요. 끄면 그날에 ‘놓침’으로 남아요.",
                    checked = state.carryOver,
                    onChange = { state.carryOver = it },
                )
                if (state.repeats) {
                    SwitchRow(
                        title = "🌅 기상 알람에 맞춰 시작",
                        description = "삼성 시계의 다음 알람이 울리고 ${gapMin}분 뒤에 시작하도록 따라 움직여요.",
                        checked = state.wakeAnchored,
                        onChange = { state.wakeAnchored = it },
                    )
                }
                if (!isJelly && !state.isNew) {
                    SwitchRow(
                        title = "사용",
                        description = "끄면 새 날부터 이 젤리를 깔지 않아요.",
                        checked = state.active,
                        onChange = { state.active = it },
                    )
                }
                Label("📝 메모")
                SoftField(
                    value = state.note,
                    onValueChange = { state.note = it.take(500) },
                    placeholder = "준비물, 장소 같은 것",
                    accent = flavor.deep,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        JellyButton(
            text = when {
                state.isNew && isJelly -> "젤리 담기"
                state.isNew -> "반복 젤리 담기"
                else -> "저장"
            },
            onClick = onSave,
            enabled = state.title.isNotBlank() && (isJelly || state.days.isNotEmpty()),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!state.isNew) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isJelly) {
                    JellyButton(
                        text = if (state.status == JellyStatus.DONE) "되돌리기" else "✓ 다 먹었어요",
                        onClick = onToggleDone,
                        filled = false,
                        color = colors.ok,
                        modifier = Modifier.weight(1f),
                        small = true,
                    )
                    if (state.date != null) {
                        JellyButton(
                            text = "보관함으로",
                            onClick = onSendToTray,
                            filled = false,
                            color = colors.text,
                            modifier = Modifier.weight(1f),
                            small = true,
                        )
                    }
                }
                JellyButton(
                    text = "버리기",
                    onClick = onDelete,
                    filled = false,
                    color = colors.danger,
                    modifier = Modifier.weight(1f),
                    small = true,
                )
            }
        }
    }

    if (pickingTime) {
        TimePickDialog(
            initial = state.start ?: 9 * 60,
            onDismiss = { pickingTime = false },
            onPick = {
                state.setStartByHand(it)
                pickingTime = false
            },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        color = LocalJellyColors.current.textSub,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

/** A text box in the same soft, borderless look as the rest of the sheet. */
@Composable
private fun SoftField(value: String, onValueChange: (String) -> Unit, placeholder: String, accent: Color) {
    val colors = LocalJellyColors.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        cursorBrush = SolidColor(accent),
        textStyle = TextStyle(color = colors.text, fontSize = 15.sp, lineHeight = 21.sp),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceSoft)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, color = colors.textSub.copy(alpha = 0.7f), fontSize = 15.sp)
                field()
            }
        },
    )
}

@Composable
private fun PreviewJelly(state: EditorState) {
    val motion = rememberJellyMotion("editor-preview", state.status == JellyStatus.DONE)
    LaunchedEffect(state.flavor) { motion.kick(1f) }
    LaunchedEffect(state.duration) { motion.kick(0.5f) }
    LaunchedEffect(state.pinned) { if (state.pinned) motion.kick(1.2f) }
    val flavor = JellyFlavors[state.flavor]
    val heightDp = (30f + Planner.MAX_DURATION.coerceAtMost(state.duration) * 0.11f + state.duration.coerceAtMost(120) * 0.25f).coerceIn(44f, 118f)
    val subtitle = listOfNotNull(
        state.start?.takeIf { state.date != null || state.kind == EditorKind.ROUTINE }?.let { hm(it) },
        durationText(state.duration),
    ).joinToString(" · ")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        JellyBody(
            flavor = flavor,
            modifier = Modifier.width(190.dp).height(heightDp.dp),
            motion = motion,
            cornerRadius = 18.dp,
        ) {
            JellyLabel(
                title = state.title.ifBlank { "말랑한 새 젤리" },
                subtitle = subtitle,
                flavor = flavor,
                motion = motion,
                heightDp = heightDp,
                compact = false,
            )
        }
        if (state.pinned) Text("📌", fontSize = 20.sp, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-8).dp))
    }
}

@Composable
private fun FlavorPicker(selected: Int, onPick: (Int) -> Unit) {
    val colors = LocalJellyColors.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // A golden jelly keeps its gold on offer, so it is not lost by an idle tap.
        val choices = JellyFlavors.all.indices.toList() + listOfNotNull(GOLDEN_FLAVOR.takeIf { selected == it })
        for (index in choices) {
            val flavor = JellyFlavors[index]
            val isSelected = index == selected
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (isSelected) flavor.deep else Color.Transparent, CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                JellyBody(
                    flavor = flavor,
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer {
                            scaleX = if (isSelected) 1.08f else 0.88f
                            scaleY = if (isSelected) 1.08f else 0.88f
                        }
                        .squishyClick { onPick(index) },
                    cornerRadius = 11.dp,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(JellyFlavors[selected].name, color = colors.textSub, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
    }
}

// ------------------------------------------------------------------ length

private const val SHORT_MAX = 120

private val TICKS = listOf(30 to "30분", 60 to "1시간", 120 to "2시간", 360 to "6시간", 720 to "12시간", 1440 to "24시간")

/** Where a length sits on the stretch bar: the first half for up to two hours, the rest up to twelve. */
internal fun lengthToFraction(minutes: Int): Float {
    val m = minutes.coerceIn(10, Planner.MAX_DURATION)
    return if (m <= SHORT_MAX) {
        0.5f * (m - 10) / (SHORT_MAX - 10).toFloat()
    } else {
        0.5f + 0.5f * (m - SHORT_MAX) / (Planner.MAX_DURATION - SHORT_MAX).toFloat()
    }
}

/** The length under a finger: 10-minute steps up to two hours, then 30-minute steps. */
internal fun fractionToLength(fraction: Float): Int {
    val f = fraction.coerceIn(0f, 1f)
    return if (f <= 0.5f) {
        (10 + (f / 0.5f * (SHORT_MAX - 10) / 10f).roundToInt() * 10).coerceIn(10, SHORT_MAX)
    } else {
        (SHORT_MAX + ((f - 0.5f) / 0.5f * (Planner.MAX_DURATION - SHORT_MAX) / 30f).roundToInt() * 30)
            .coerceIn(SHORT_MAX, Planner.MAX_DURATION)
    }
}

/**
 * Pull the jelly to make it longer: it stretches with the finger and ticks under it every step,
 * like a chewy sweet being pulled. Tapping the track jumps there.
 */
@Composable
private fun StretchLength(minutes: Int, flavor: JellyFlavor, onChange: (Int) -> Unit) {
    val colors = LocalJellyColors.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val current by rememberUpdatedState(minutes)
    val change by rememberUpdatedState(onChange)
    val motion = rememberJellyMotion("stretch", false)
    LaunchedEffect(minutes) { motion.kick(0.35f) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val trackWidth = constraints.maxWidth.toFloat()
        val knob = with(density) { 30.dp.toPx() }
        val usable = (trackWidth - knob).coerceAtLeast(1f)
        fun lengthAt(x: Float) = fractionToLength((x - knob / 2f) / usable)
        fun set(next: Int) {
            if (next != current) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                change(next)
            }
        }
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(colors.surfaceSoft)
                    .pointerInput(Unit) {
                        detectTapGestures { set(lengthAt(it.x)) }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            set(lengthAt(change.position.x))
                        }
                    },
            ) {
                val fraction = lengthToFraction(minutes)
                val barWidth = with(density) { (knob + usable * fraction).toDp() }
                JellyBody(
                    flavor = flavor,
                    modifier = Modifier.width(barWidth.coerceAtLeast(64.dp)).height(54.dp),
                    motion = motion,
                    cornerRadius = 27.dp,
                    idle = false,
                ) {
                    Text(
                        durationText(minutes),
                        color = flavor.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp, end = 34.dp),
                    )
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 6.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⇢", color = flavor.deep, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                val labelWidth = with(density) { 40.dp.toPx() }
                for ((mark, label) in TICKS) {
                    val center = knob / 2f + usable * lengthToFraction(mark)
                    val left = (center - labelWidth / 2f).coerceIn(0f, (trackWidth - labelWidth).coerceAtLeast(0f))
                    Text(
                        label,
                        color = if (mark == minutes) flavor.deep else colors.textSub,
                        fontSize = 10.sp,
                        fontWeight = if (mark == minutes) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.width(40.dp).offset(x = with(density) { left.toDp() }),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ when

@Composable
private fun WhenPicker(state: EditorState, today: LocalDate, sundayFirst: Boolean) {
    val colors = LocalJellyColors.current
    var calendar by remember { mutableStateOf(false) }
    fun pick(date: LocalDate) {
        state.date = date
        if (state.start == null) state.start = 9 * 60
    }
    val other = state.date?.takeIf { it != today && it != today.plusDays(1) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JellyChip("오늘", selected = state.date == today, onClick = { pick(today) })
        JellyChip("내일", selected = state.date == today.plusDays(1), onClick = { pick(today.plusDays(1)) })
        JellyChip(
            other?.let { "${it.monthValue}/${it.dayOfMonth} ${dayName(it)}" } ?: "날짜 고르기",
            selected = other != null || calendar,
            onClick = { calendar = !calendar },
        )
        JellyChip(
            "보관함에 두기",
            selected = state.date == null,
            onClick = {
                state.date = null
                state.start = null
                state.pinned = false
                calendar = false
            },
            accent = colors.textSub,
        )
    }
    AnimatedVisibility(visible = calendar) {
        MiniMonth(
            selected = state.date ?: today,
            today = today,
            sundayFirst = sundayFirst,
            onPick = {
                pick(it)
                calendar = false
            },
            modifier = Modifier.padding(top = 10.dp),
        )
    }
    state.date?.let { date ->
        val holiday = KoreanHolidays.on(date)?.name
        Text(
            listOfNotNull(dateTitle(date), holiday).joinToString(" · "),
            color = if (holiday != null) colors.sunday else colors.textSub,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )
    }
}

/** A small month to pick a day from, with today, the picked day and holidays marked. */
@Composable
private fun MiniMonth(selected: LocalDate, today: LocalDate, sundayFirst: Boolean, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalJellyColors.current
    val type = LocalJellyType.current
    var month by remember { mutableStateOf(selected.withDayOfMonth(1)) }
    val days = Planner.monthGrid(month, sundayFirst)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceSoft)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = colors.text, fontSize = 20.sp, modifier = Modifier.clickableNoRipple { month = month.minusMonths(1) }.padding(horizontal = 10.dp))
            Text(
                "${month.year}년 ${month.monthValue}월",
                color = colors.text,
                fontSize = 15.sp,
                fontFamily = type.display,
                fontWeight = type.displayWeight,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text("›", color = colors.text, fontSize = 20.sp, modifier = Modifier.clickableNoRipple { month = month.plusMonths(1) }.padding(horizontal = 10.dp))
        }
        for (week in days.chunked(7)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                for (date in week) {
                    val inMonth = date.monthValue == month.monthValue
                    val isSelected = date == selected
                    val tint = when {
                        isSelected -> colors.onAccent
                        date.dayOfWeek.value == 7 || KoreanHolidays.on(date) != null -> colors.sunday
                        date.dayOfWeek.value == 6 -> colors.saturday
                        else -> colors.text
                    }
                    Box(Modifier.weight(1f).height(34.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> colors.accent
                                        date == today -> colors.accent.copy(alpha = 0.16f)
                                        else -> Color.Transparent
                                    },
                                )
                                .clickableNoRipple { onPick(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${date.dayOfMonth}",
                                color = tint.copy(alpha = if (inMonth) 1f else 0.35f),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected || date == today) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The start, ten minutes at a time or picked on a clock. Under it: the words of the title a new
 * jelly's time was taken from, or for an existing jelly the title's time in one tap.
 */
@Composable
private fun TimeRow(state: EditorState, onPick: () -> Unit) {
    Column {
        TimeChips(state, onPick)
        val colors = LocalJellyColors.current
        val fromTitle = state.timeFromTitle
        val said = if (state.isNew) null else TitleTime.find(state.title)
        if (fromTitle != null) {
            Text(
                keepWords("시간을 제목의 ‘$fromTitle’에 맞췄어요."),
                color = colors.textSub,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else if (said != null && said.minute != state.start) {
            JellyChip(
                "제목대로 ${hm(said.minute)}",
                selected = false,
                onClick = { state.setStartByHand(said.minute) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun TimeChips(state: EditorState, onPick: () -> Unit) {
    val colors = LocalJellyColors.current
    val start = state.start ?: 9 * 60
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JellyChip("−10분", selected = false, onClick = { state.setStartByHand(start - 10) })
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.accent.copy(alpha = 0.12f),
            modifier = Modifier.squishyClick(onClick = onPick),
        ) {
            Text(
                hm(start),
                color = colors.text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        JellyChip("+10분", selected = false, onClick = { state.setStartByHand(start + 10) })
        Text("→ ${endText(start, state.duration)}", color = colors.textSub, fontSize = 13.sp)
    }
}

/**
 * "시작 전 알람": asks the clock app for an alarm a little before the jelly starts. The request takes
 * a time but no date: in the coming 24 hours the alarm is set in one tap, and a later one is made in
 * Samsung Clock's new-alarm screen, where the date is picked.
 */
@Composable
private fun AlarmRow(
    state: EditorState,
    now: LocalDateTime,
    onSetAlarm: ((Int, String) -> Boolean)?,
    onPickDate: ((Int, String) -> Boolean)?,
) {
    val colors = LocalJellyColors.current
    val date = state.date
    val start = state.start
    val at = if (date != null && start != null) {
        LocalDateTime.of(date, LocalTime.MIDNIGHT).plusMinutes((start - state.alarmBefore).toLong())
    } else {
        null
    }
    val ahead = at?.let { Duration.between(now, it) }
    // The clock app takes a time but no date, so only the coming 24 hours are safe in one tap.
    val possible = onSetAlarm != null && ahead != null && !ahead.isNegative && ahead.toHours() < 24
    val later = onPickDate != null && ahead != null && ahead.toHours() >= 24
    val atMinute = at?.let { it.hour * 60 + it.minute }
    val title = state.title.trim().ifBlank { "젤리" }
    val label = if (state.alarmBefore == 0) "$title 시작" else "$title ${state.alarmBefore}분 전"
    // The alarm already asked for in this sheet, so it is not asked for twice by accident.
    var asked by remember { mutableStateOf<LocalDateTime?>(null) }
    var failed by remember { mutableStateOf(false) }
    // Samsung Clock was opened for this alarm (whether it was saved there is not known).
    var opened by remember { mutableStateOf<LocalDateTime?>(null) }
    var asking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("⏰ 시작 전 알람", color = colors.text, fontSize = 15.sp)
        Text(
            keepWords(
                when {
                    onSetAlarm == null -> "이 기기에서는 시계 알람을 맞출 수 없어요."
                    failed -> "알람을 맞출 시계 앱을 찾지 못했어요."
                    asked != null && asked == at -> "${hm(atMinute!!)} 알람을 시계 앱에 부탁했어요. 띠링!"
                    at == null -> "날짜와 시간이 있는 젤리에 알람을 맞출 수 있어요."
                    possible -> "${hm(atMinute!!)}에 울리도록 시계 앱에 알람을 맞춰요."
                    later && opened == at -> "삼성 시계에서 날짜를 골라 저장했다면 다 됐어요."
                    later -> "하루 넘게 남은 알람은 삼성 시계에서 날짜를 골라 맞춰요."
                    ahead != null && ahead.isNegative -> "이미 지난 시각이에요."
                    else -> "시계 앱은 날짜 없이 시각만 받아서, 24시간 안에 울릴 알람만 맞출 수 있어요."
                },
            ),
            color = if (failed) colors.danger else colors.textSub,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for ((before, chip) in listOf(0 to "정각", 10 to "10분 전", 30 to "30분 전")) {
                JellyChip(chip, selected = state.alarmBefore == before, onClick = { state.alarmBefore = before })
            }
            Spacer(Modifier.weight(1f))
            JellyButton(
                text = when {
                    asked != null && asked == at -> "✓ 맞췄어요"
                    later -> "날짜 골라 맞추기"
                    else -> "알람 맞추기"
                },
                onClick = {
                    if (possible && at != null && atMinute != null) {
                        val ok = onSetAlarm?.invoke(atMinute, label) == true
                        failed = !ok
                        asked = if (ok) at else null
                    } else if (later) {
                        asking = true
                    }
                },
                enabled = (possible && asked != at) || later,
                small = true,
            )
        }
    }
    if (asking && at != null && atMinute != null) {
        AlarmDateDialog(
            date = at.toLocalDate(),
            minute = atMinute,
            onOpen = {
                asking = false
                if (onPickDate?.invoke(atMinute, label) == true) opened = at
            },
            onDismiss = { asking = false },
        )
    }
}

/**
 * "반복": on days of the week, or every few days ("6일마다", a duty every six days). A jelly that
 * becomes repeating counts its days from its own date; a repeating jelly picks the day here.
 */
@Composable
private fun RepeatPicker(state: EditorState, allowNone: Boolean, today: LocalDate) {
    val colors = LocalJellyColors.current
    val cycle = state.everyDays >= 2
    fun onDays(days: Set<Int>) {
        state.days = days
        state.everyDays = 0
        if (days.isNotEmpty()) state.pinned = false
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (allowNone) JellyChip("안 함", selected = !cycle && state.days.isEmpty(), onClick = { onDays(emptySet()) })
        JellyChip("매일", selected = !cycle && state.days == ALL_DAYS, onClick = { onDays(ALL_DAYS) })
        JellyChip("평일", selected = !cycle && state.days == WEEKDAYS, onClick = { onDays(WEEKDAYS) })
        JellyChip("주말", selected = !cycle && state.days == WEEKEND, onClick = { onDays(WEEKEND) })
        JellyChip("며칠마다", selected = cycle, onClick = {
            if (!cycle) {
                state.everyDays = 2
                if (state.cycleStart == null) state.cycleStart = state.date ?: today
                if (state.days.isEmpty()) state.days = ALL_DAYS
                state.pinned = false
            }
        })
    }
    if (cycle) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            JellyChip("−", selected = false, onClick = { state.everyDays = (state.everyDays - 1).coerceAtLeast(2) })
            Text("${state.everyDays}일마다", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            JellyChip("+", selected = false, onClick = { state.everyDays = (state.everyDays + 1).coerceAtMost(30) })
        }
        Spacer(Modifier.height(8.dp))
        val jellyDate = state.date
        if (state.kind == EditorKind.JELLY && jellyDate != null) {
            Text(
                keepWords("이 젤리의 날(${shortDate(jellyDate)})부터 ${state.everyDays}일마다 깔려요."),
                color = colors.textSub,
                fontSize = 12.sp,
            )
        } else {
            val start = state.cycleStart ?: today
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JellyChip("‹", selected = false, onClick = { state.cycleStart = start.minusDays(1) })
                Text("${shortDate(start)}부터", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                JellyChip("›", selected = false, onClick = { state.cycleStart = start.plusDays(1) })
                if (start != today) JellyChip("오늘", selected = false, onClick = { state.cycleStart = today })
            }
            Text(
                keepWords("이 날이 반복의 첫날이에요. 그 뒤로 ${state.everyDays}일마다 깔려요."),
                color = colors.textSub,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        return
    }
    DayCircles(state.days, allowNone) { onDays(it) }
}

@Composable
private fun DayCircles(days: Set<Int>, allowNone: Boolean, onChange: (Set<Int>) -> Unit) {
    val colors = LocalJellyColors.current
    if (days.isNotEmpty() || !allowNone) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (day in listOf(7, 1, 2, 3, 4, 5, 6)) {
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
fun TimePickDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit, title: String = "몇 시에 할까요?") {
    val colors = LocalJellyColors.current
    val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surface) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("좋아요") }
                }
            }
        }
    }
}
