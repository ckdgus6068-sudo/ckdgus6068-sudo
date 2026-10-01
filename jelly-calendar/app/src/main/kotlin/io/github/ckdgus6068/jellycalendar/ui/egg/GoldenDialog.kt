package io.github.ckdgus6068.jellycalendar.ui.egg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.ckdgus6068.jellycalendar.core.GOLDEN_GRABS
import io.github.ckdgus6068.jellycalendar.core.GoldenFind
import io.github.ckdgus6068.jellycalendar.ui.common.JellyButton
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyBody
import io.github.ckdgus6068.jellycalendar.ui.jelly.rememberJellyMotion
import io.github.ckdgus6068.jellycalendar.ui.keepWords
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyFlavors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Who finders are sent to. The shared page says the same (docs/share/app.js: MAKER). */
const val MAKER_NAME = "황OO"

private val FOUND_AT = DateTimeFormatter.ofPattern("yyyy년 M월 d일 a h:mm", Locale.KOREAN)

/** When [find] was made, as people write it: "2026년 10월 1일 오후 3:12". */
fun foundAtText(find: GoldenFind, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(find.foundAt).atZone(zone).format(FOUND_AT)

/** What "제작자에게 보내기" sends, so the maker gets the code without a screenshot too. */
fun goldenShareText(find: GoldenFind): String =
    "🏆 젤리 캘린더에서 숨겨진 황금 젤리를 찾았어요!\n황금 코드: ${find.code}\n찾은 때: ${foundAtText(find)}"

/** The surprise when the hidden golden jelly is found, and later from settings. */
@Composable
fun GoldenDialog(find: GoldenFind, onShare: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalJellyColors.current
    val type = LocalJellyType.current
    val motion = rememberJellyMotion("golden", false)
    LaunchedEffect(Unit) { motion.kick(1.4f) }
    val when_ = remember(find) { foundAtText(find) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surface) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                JellyBody(
                    flavor = JellyFlavors.golden,
                    modifier = Modifier.size(width = 132.dp, height = 88.dp),
                    motion = motion,
                    cornerRadius = 32.dp,
                ) {
                    Text("🏆", fontSize = 36.sp, modifier = Modifier.align(Alignment.Center))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "숨겨진 황금 젤리를\n찾았어요!",
                    color = colors.text,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontFamily = type.display,
                    fontWeight = type.displayWeight,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    keepWords("젤리를 ${GOLDEN_GRABS}번이나 쉬지 않고 주물럭거린 끈기에 젤리가 황금으로 변했어요."),
                    color = colors.textSub,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    keepWords("이 화면을 캡처해서 제작자 ${MAKER_NAME}에게 보내 주세요. 작은 선물을 드려요!"),
                    color = colors.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFFFF3C4))
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("황금 코드", color = Color(0xFF8A6100), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        find.code,
                        color = Color(0xFF5C3B00),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Text("찾은 때 · $when_", color = Color(0xFF8A6100), fontSize = 12.sp)
                }
                Spacer(Modifier.height(18.dp))
                JellyButton(
                    text = "제작자에게 보내기",
                    onClick = { onShare(goldenShareText(find)) },
                    color = Color(0xFFE09A00),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onDismiss) { Text("닫기", color = colors.textSub) }
                }
            }
        }
    }
}
