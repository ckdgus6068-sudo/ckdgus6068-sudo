package io.github.ckdgus6068.jellycalendar.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ckdgus6068.jellycalendar.core.Jelly
import io.github.ckdgus6068.jellycalendar.core.JellyStatus
import io.github.ckdgus6068.jellycalendar.core.WakeStatus
import io.github.ckdgus6068.jellycalendar.ui.alarmSourceName
import io.github.ckdgus6068.jellycalendar.ui.common.JellyButton
import io.github.ckdgus6068.jellycalendar.ui.hm
import io.github.ckdgus6068.jellycalendar.ui.hmTo
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * The link between the alarm clock and the first jelly of the day, with one-tap fixes when
 * they drifted apart.
 */
@Composable
fun WakeCard(
    status: WakeStatus,
    gapMin: Int,
    source: String?,
    requestedMin: Int?,
    onSetAlarm: (minute: Int, label: String) -> Unit,
    onMoveFirst: (Jelly, Int) -> Unit,
    onOpenAlarms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status is WakeStatus.Empty) return
    val colors = LocalJellyColors.current
    val sourceName = source?.let { alarmSourceName(it) }

    var title: String
    var subtitle: String
    val actions = ArrayList<Pair<String, () -> Unit>>()
    var tone = colors.wake
    when (status) {
        is WakeStatus.Synced -> {
            val first = status.first
            title = if (status.alarmPassed) {
                "${hm(status.alarmMin)}에 일어났어요 · ${hm(first.startMin ?: 0)} ${first.title}"
            } else {
                "기상 ${hm(status.alarmMin)} → ${hm(first.startMin ?: 0)} ${first.title}"
            }
            subtitle = "알람과 첫 일과가 맞춰져 있어요" + (sourceName?.let { " · $it" } ?: "")
            tone = colors.ok
        }
        is WakeStatus.Mismatch -> {
            val first = status.first
            val start = first.startMin ?: 0
            if (status.alarmPassed) {
                title = "${hm(status.alarmMin)}에 일어났어요 · ${hm(start)} ${first.title}"
                subtitle = "오늘 아침 알람은 이미 지나갔어요"
            } else if (requestedMin == status.suggestedAlarm) {
                title = "알람 ${hm(status.alarmMin)} · 첫 일과 ${hm(start)} ${first.title}"
                subtitle = "${hm(requestedMin)} 알람을 요청했어요. 예전 ${hm(status.alarmMin)} 알람은 시계 앱에서 꺼 주세요."
            } else {
                title = "알람 ${hm(status.alarmMin)} · 첫 일과 ${hm(start)} ${first.title}"
                subtitle = "알람 뒤 ${start - status.alarmMin}분 만에 시작해요 (설정은 ${gapMin}분)"
                if (status.canSetNow) {
                    actions += "알람을 ${hmTo(status.suggestedAlarm)}" to {
                        onSetAlarm(status.suggestedAlarm, "젤리 · ${first.title}")
                    }
                }
                if (first.status == JellyStatus.PLANNED) {
                    val fixed = status.alarmMin + gapMin
                    actions += "첫 일과를 ${hmTo(fixed)}" to { onMoveFirst(first, fixed) }
                }
            }
        }
        is WakeStatus.NoAlarm -> {
            val first = status.first
            title = "첫 일과 ${hm(first.startMin ?: 0)} ${first.title}"
            if (requestedMin == status.suggestedAlarm) {
                subtitle = "${hm(requestedMin)} 알람을 요청했어요. 시계 앱에 저장됐는지 확인해 주세요."
            } else if (status.canSetNow) {
                subtitle = "기상 알람이 아직 없어요. ${gapMin}분 전에 깨워 드릴까요?"
                actions += "알람 ${hm(status.suggestedAlarm)} 맞추기" to {
                    onSetAlarm(status.suggestedAlarm, "젤리 · ${first.title}")
                }
            } else {
                subtitle = "전날 저녁부터 ${hm(status.suggestedAlarm)} 알람을 맞출 수 있어요"
            }
        }
        is WakeStatus.AlarmOnly -> {
            title = if (status.alarmPassed) "${hm(status.alarmMin)}에 일어났어요" else "기상 ${hm(status.alarmMin)}"
            subtitle = "이 날 아침 일과가 아직 없어요"
        }
        WakeStatus.Empty -> return
    }
    actions += "알람 목록" to onOpenAlarms

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(tone.copy(alpha = if (colors.isDark) 0.16f else 0.11f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SunIcon(tone, Modifier.size(22.dp))
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    title,
                    color = colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(subtitle, color = colors.textSub, fontSize = 12.sp, maxLines = 2)
            }
        }
        if (actions.isNotEmpty()) {
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions.forEachIndexed { index, (label, action) ->
                    JellyButton(
                        text = label,
                        onClick = action,
                        filled = index == 0 && actions.size > 1,
                        color = if (index == 0 && actions.size > 1) colors.accent else colors.text,
                        small = true,
                    )
                }
            }
        }
    }
}

@Composable
fun SunIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.24f
        drawCircle(color, r, c)
        for (i in 0 until 8) {
            val a = (i * Math.PI / 4).toFloat()
            val start = Offset(c.x + cos(a) * r * 1.5f, c.y + sin(a) * r * 1.5f)
            val end = Offset(c.x + cos(a) * r * 2.05f, c.y + sin(a) * r * 2.05f)
            drawLine(color, start, end, strokeWidth = size.minDimension * 0.09f, cap = StrokeCap.Round)
        }
    }
}
