package io.github.ckdgus6068.jellycalendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ckdgus6068.jellycalendar.core.Jelly
import io.github.ckdgus6068.jellycalendar.core.KoreanHolidays
import io.github.ckdgus6068.jellycalendar.ui.common.JellyButton
import io.github.ckdgus6068.jellycalendar.ui.common.squishyClick
import io.github.ckdgus6068.jellycalendar.ui.dateTitle
import io.github.ckdgus6068.jellycalendar.ui.durationText
import io.github.ckdgus6068.jellycalendar.ui.hm
import io.github.ckdgus6068.jellycalendar.ui.keepWords
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyFlavors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyType
import java.time.LocalDate

/**
 * A jelly from the tray was dropped on [date]: the person says when, and the app only suggests
 * the time it was first meant for ([original], when it had one) and a few free times of that day.
 */
@Composable
fun PlaceSheet(
    jelly: Jelly,
    date: LocalDate,
    original: Int?,
    originalFree: Boolean,
    suggestions: List<Int>,
    onPick: (Int) -> Unit,
    onPickOther: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalJellyColors.current
    val type = LocalJellyType.current
    val holiday = KoreanHolidays.on(date)?.name
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            "언제 먹을까요?",
            color = colors.text,
            fontSize = 21.sp,
            fontFamily = type.display,
            fontWeight = type.displayWeight,
        )
        Text(
            keepWords(listOfNotNull("‘${jelly.title}’ · ${durationText(jelly.durationMin)} → ${dateTitle(date)}", holiday).joinToString(" · ")),
            color = colors.textSub,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (original != null) {
            Caption("원래 하려던 시간")
            TimeOption(
                jelly = jelly,
                start = original,
                note = if (originalFree) "비어 있어요" else "다른 젤리와 겹쳐요",
                onClick = { onPick(original) },
            )
        }

        Caption("비어 있는 시간")
        if (suggestions.isEmpty()) {
            Text(
                keepWords("이 날은 넉넉하게 빈 시간이 없어요. 직접 골라 주세요."),
                color = colors.textSub,
                fontSize = 13.sp,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (start in suggestions) TimeOption(jelly, start, note = null, onClick = { onPick(start) })
            }
        }

        Spacer(Modifier.height(16.dp))
        JellyButton(
            text = "직접 고를래요",
            onClick = onPickOther,
            filled = false,
            color = colors.text,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onKeep, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("보관함에 그대로 둘래요", color = colors.textSub)
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        color = LocalJellyColors.current.textSub,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

/** One start time to pick, tinted with the jelly's own flavour. */
@Composable
private fun TimeOption(jelly: Jelly, start: Int, note: String?, onClick: () -> Unit) {
    val colors = LocalJellyColors.current
    val flavor = JellyFlavors[jelly.flavor]
    Row(
        Modifier
            .fillMaxWidth()
            .squishyClick(onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(flavor.light)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(hm(start), color = flavor.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            " ~ ${hm(start + jelly.durationMin)}",
            color = flavor.ink.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (note != null) Text(note, color = flavor.ink.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}
