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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ckdgus6068.jellycalendar.core.Routine
import io.github.ckdgus6068.jellycalendar.ui.common.JellyButton
import io.github.ckdgus6068.jellycalendar.ui.common.squishyClick
import io.github.ckdgus6068.jellycalendar.ui.daysText
import io.github.ckdgus6068.jellycalendar.ui.durationText
import io.github.ckdgus6068.jellycalendar.ui.hm
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyBody
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyFlavors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors

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
        Text(title, color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** The jellies that are laid down automatically, day after day. */
@Composable
fun RoutinesScreen(
    routines: List<Routine>,
    gapMin: Int,
    onBack: () -> Unit,
    onEdit: (Routine) -> Unit,
    onNew: () -> Unit,
    onToggleActive: (Routine, Boolean) -> Unit,
) {
    val colors = LocalJellyColors.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("반복 젤리", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "여기 있는 젤리는 정한 요일마다 자동으로 깔려요. 시간과 길이를 바꾸면 오늘부터 손대지 않은 날에 반영돼요.",
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
            Spacer(Modifier.height(32.dp))
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
                "${daysText(routine.days)} · $time · ${durationText(routine.durationMin)}",
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
