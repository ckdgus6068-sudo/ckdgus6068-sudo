package io.github.ckdgus6068.jellycalendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.ckdgus6068.jellycalendar.core.Look
import io.github.ckdgus6068.jellycalendar.core.LookBook
import io.github.ckdgus6068.jellycalendar.ui.common.squishyClick
import io.github.ckdgus6068.jellycalendar.ui.jelly.LookAvatar
import io.github.ckdgus6068.jellycalendar.ui.keepWords
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyFlavors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyType

/** The colour characters are shown in here (the shared calendar shows each person's own colour). */
val LookPreviewColor: Color get() = JellyFlavors[0].base

/** "경찰관", "얼굴" for just a face, "민무늬" for a plain jelly. */
fun lookName(look: Look?): String {
    val job = look?.let { LookBook.job(it.job) } ?: return "민무늬"
    return if (job.id == "face") "얼굴" else job.name
}

/** "내 캐릭터": my character, tap to pick another. */
@Composable
fun LookRow(look: Look, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalJellyColors.current
    Row(
        modifier
            .fillMaxWidth()
            .squishyClick(onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceSoft)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LookAvatar(look, LookPreviewColor, 64.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(lookName(look), color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                keepWords("눌러서 바꾸기 · 공유 달력에서도 이 모습이에요"),
                color = colors.textSub,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Text("›", color = colors.textSub, fontSize = 24.sp)
    }
}

/** "내 캐릭터 고르기": a plain jelly, the face, and every job in its two outfits. */
@Composable
fun LookPickerDialog(current: Look, onPick: (Look) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        LookPickerContent(current, onPick, onDismiss, Modifier.fillMaxHeight(0.88f))
    }
}

/** What [LookPickerDialog] shows. */
@Composable
fun LookPickerContent(current: Look, onPick: (Look) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalJellyColors.current
    val type = LocalJellyType.current
    val choices = buildList {
        add(Look.PLAIN to "민무늬")
        for (job in LookBook.jobs) for (v in 0..1) add(Look(job.id, v) to "${if (job.id == "face") "얼굴" else job.name} ${v + 1}")
    }
    Surface(shape = RoundedCornerShape(26.dp), color = colors.surface, modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Text(
                "내 캐릭터 고르기",
                color = colors.text,
                fontSize = 20.sp,
                fontFamily = type.display,
                fontWeight = type.displayWeight,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Text(
                keepWords("공유 달력에서 내가 올린 젤리와 내 동그라미가 이 모습이 돼요. 직업마다 두 가지가 있어요."),
                color = colors.textSub,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(choices, key = { "${it.first.job}-${it.first.v}" }) { (look, label) ->
                    val picked = look == current || (look.plain && current.plain)
                    Column(
                        Modifier
                            .squishyClick { onPick(look) }
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceSoft)
                            .border(2.dp, if (picked) colors.accent else Color.Transparent, RoundedCornerShape(16.dp))
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        LookAvatar(look, LookPreviewColor, 54.dp)
                        Text(
                            label,
                            color = colors.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("닫기", color = colors.textSub)
            }
        }
    }
}
