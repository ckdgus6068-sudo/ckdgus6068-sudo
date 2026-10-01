package io.github.ckdgus6068.jellycalendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ckdgus6068.jellycalendar.core.Routine
import io.github.ckdgus6068.jellycalendar.core.ShiftPatterns
import io.github.ckdgus6068.jellycalendar.ui.common.JellyChip
import io.github.ckdgus6068.jellycalendar.ui.common.JellyButton
import io.github.ckdgus6068.jellycalendar.ui.common.squishyClick
import io.github.ckdgus6068.jellycalendar.ui.dateTitle
import io.github.ckdgus6068.jellycalendar.ui.range
import io.github.ckdgus6068.jellycalendar.ui.repeatText
import io.github.ckdgus6068.jellycalendar.ui.shortDate
import io.github.ckdgus6068.jellycalendar.ui.durationText
import io.github.ckdgus6068.jellycalendar.ui.hm
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyBody
import io.github.ckdgus6068.jellycalendar.ui.keepWords
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyFlavors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyType

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    val colors = LocalJellyColors.current
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            @Suppress("DEPRECATION")
            Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = colors.text)
        }
        Text(
            title,
            color = colors.text,
            fontSize = 20.sp,
            fontFamily = LocalJellyType.current.display,
            fontWeight = LocalJellyType.current.displayWeight,
        )
    }
}

/**
 * The jellies that are laid down automatically, day after day; and shift work ([onAddPattern]) laid
 * down as repeating jellies in one go.
 */
@Composable
fun RoutinesScreen(
    routines: List<Routine>,
    gapMin: Int,
    onBack: () -> Unit,
    onEdit: (Routine) -> Unit,
    onNew: () -> Unit,
    onToggleActive: (Routine, Boolean) -> Unit,
    today: LocalDate = LocalDate.now(),
    onAddPattern: (ShiftPatterns.Pattern, LocalDate) -> Unit = { _, _ -> },
) {
    val colors = LocalJellyColors.current
    var addingPattern by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("반복 젤리", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                keepWords("여기 있는 젤리는 정한 요일마다, 또는 며칠마다 자동으로 깔려요. 시간과 길이를 바꾸면 오늘부터 손대지 않은 날에 반영돼요."),
                color = colors.textSub,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(14.dp))
            if (routines.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surfaceSoft)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("아직 반복 젤리가 없어요", color = colors.textSub, fontSize = 14.sp)
                }
            }
            for (routine in routines.sortedBy { it.startMin }) {
                RoutineCard(routine, gapMin, onEdit, onToggleActive)
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(10.dp))
            JellyButton("+ 반복 젤리 만들기", onClick = onNew, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            JellyButton(
                "근무 패턴 넣기 (주야휴비 · 당직)",
                onClick = { addingPattern = true },
                filled = false,
                color = colors.text,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(32.dp))
        }
    }
    if (addingPattern) {
        ShiftPatternDialog(
            today = today,
            onAdd = { pattern, firstDay ->
                addingPattern = false
                onAddPattern(pattern, firstDay)
            },
            onDismiss = { addingPattern = false },
        )
    }
}

/**
 * "근무 패턴 넣기": 주야휴비 (day, night, two days off) or a 24-hour duty every few days, from a
 * first working day, with the first cycle written out before anything is laid down.
 */
@Composable
private fun ShiftPatternDialog(
    today: LocalDate,
    onAdd: (ShiftPatterns.Pattern, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalJellyColors.current
    val type = LocalJellyType.current
    var duty by remember { mutableStateOf(false) }
    var every by remember { mutableStateOf(6) }
    var first by remember { mutableStateOf(today) }
    val pattern = if (duty) ShiftPatterns.duty(every) else ShiftPatterns.DAY_NIGHT_OFF
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(26.dp), color = colors.surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)) {
                Text("근무 패턴 넣기", color = colors.text, fontSize = 20.sp, fontFamily = type.display, fontWeight = type.displayWeight)
                Text(
                    keepWords("교대 근무를 반복 젤리로 한 번에 깔아요. 넣은 뒤에 시간이나 이름을 하나씩 고칠 수 있어요."),
                    color = colors.textSub,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JellyChip("주야휴비 (4일)", selected = !duty, onClick = { duty = false })
                    JellyChip("당직", selected = duty, onClick = { duty = true })
                }
                if (duty) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        JellyChip("−", selected = false, onClick = { every = (every - 1).coerceAtLeast(2) })
                        Text("${every}일마다 하루", color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        JellyChip("+", selected = false, onClick = { every = (every + 1).coerceAtMost(30) })
                    }
                }
                Text(
                    "첫 근무일",
                    color = colors.textSub,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JellyChip("‹", selected = false, onClick = { first = first.minusDays(1) })
                    Text(dateTitle(first), color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    JellyChip("›", selected = false, onClick = { first = first.plusDays(1) })
                }
                if (first != today) {
                    JellyChip("오늘로", selected = false, onClick = { first = today }, modifier = Modifier.padding(top = 6.dp))
                }
                // The first cycle, day by day.
                Column(
                    Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceSoft)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (i in 0 until pattern.cycle) {
                        val day = first.plusDays(i.toLong())
                        val shift = pattern.shifts.firstOrNull { it.offset == i }
                        val name = pattern.dayNames.getOrNull(i).orEmpty()
                        val line = when {
                            shift != null -> "${shortDate(day)}  ${shift.title} ${range(shift.startMin, shift.durationMin)}"
                            name.isNotEmpty() -> "${shortDate(day)}  $name"
                            else -> null
                        } ?: continue
                        Text(line, color = colors.text, fontSize = 13.sp)
                    }
                    Text(
                        "그 뒤로 ${pattern.cycle}일마다 되풀이돼요.",
                        color = colors.textSub,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("취소", color = colors.textSub) }
                    JellyButton("넣기", onClick = { onAdd(pattern, first) })
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    gapMin: Int,
    onEdit: (Routine) -> Unit,
    onToggleActive: (Routine, Boolean) -> Unit,
) {
    val colors = LocalJellyColors.current
    val flavor = JellyFlavors[routine.flavor]
    Row(
        Modifier
            .fillMaxWidth()
            .squishyClick { onEdit(routine) }
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JellyBody(
            flavor = flavor,
            modifier = Modifier.width(44.dp).height((22f + routine.durationMin * 0.35f).coerceIn(28f, 64f).dp),
            cornerRadius = 12.dp,
        )
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                routine.title,
                color = if (routine.active) colors.text else colors.textSub,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val time = if (routine.wakeAnchored) "기상 +${gapMin}분 (기본 ${hm(routine.startMin)})" else hm(routine.startMin)
            Text(
                "${repeatText(routine)} · $time · ${durationText(routine.durationMin)}",
                color = colors.textSub,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (routine.wakeAnchored) Tag("기상 연동", colors.wake)
                if (routine.carryOver) Tag("못 하면 보관함", colors.accent) else Tag("놓치면 그날에", colors.textSub)
            }
        }
        Switch(
            checked = routine.active,
            onCheckedChange = { onToggleActive(routine, it) },
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
        )
    }
}

@Composable
private fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
