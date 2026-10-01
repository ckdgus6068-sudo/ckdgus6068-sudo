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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ckdgus6068.jellycalendar.core.Settings
import io.github.ckdgus6068.jellycalendar.ui.box.boxColor
import io.github.ckdgus6068.jellycalendar.ui.common.JellyButton
import io.github.ckdgus6068.jellycalendar.ui.keepWords
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyType

/** One part of the how-to: a heading and the steps under it. */
private class GuidePart(val flavor: Int, val title: String, val lines: List<String>)

private fun guideParts(settings: Settings, gapMin: Int): List<GuidePart> {
    val finish = listOfNotNull(
        if (settings.doneByDoubleTap) "두 번 톡" else null,
        if (settings.doneByLongPress) "꾹 눌렀다 떼기" else null,
    ).joinToString(" 또는 ")
    val finishLine = if (finish.isEmpty()) {
        "완료: 젤리를 톡 눌러 열고 ‘완료’를 누르세요. 두 번 톡이나 꾹 눌렀다 떼기로 완료하려면 설정에서 켜세요."
    } else {
        "$finish: 완료해요. 한 번 더 하면 완료가 취소돼요."
    }
    return listOf(
        GuidePart(
            5,
            "화면 고르기",
            listOf(
                "맨 위 ‘내 젤리 · 공유 젤리 · 모두’ 탭으로 무엇을 볼지 골라요.",
                "내 젤리: 이 휴대폰에만 있는 내 일정이에요. 그 아래 ‘상자 · 주 · 일’ 버튼으로 보는 방식을 바꿔요.",
                "상자: 하루를 상자 하나로 보여 줘요. 그날 젤리가 시작 시간 순서대로 떨어져 쌓이고, 오래 걸리는 일일수록 젤리가 커요.",
                "주 · 일: 시간표로 보여 줘요. 몇 시에 무엇을 하는지 한눈에 볼 수 있어요.",
                "화살표나 날짜 칸을 누르면 다른 날로 가고, 맨 위 날짜 제목을 누르면 오늘로 돌아와요.",
            ),
        ),
        GuidePart(
            0,
            "젤리 만들기",
            listOf(
                "‘+’ 버튼(상자에서는 오른쪽 위, 주 · 일에서는 오른쪽 아래)을 누르고 이름, 색, 길이, 날짜와 시작 시간을 정한 뒤 ‘저장’을 누르세요.",
                "주 · 일 시간표에서는 빈 곳을 꾹 누르면 그 시간으로 새 젤리 창이 열려요.",
                "보관함의 ‘+ 할 일’을 누르면 날짜를 정하지 않은 젤리를 보관함에 넣어 둘 수 있어요.",
            ),
        ),
        GuidePart(
            3,
            "상자에서 젤리 다루기",
            listOf(
                "톡: 젤리를 열어 이름, 시간, 길이를 고쳐요.",
                finishLine,
                "끌기: 젤리를 잡고 흔들 수 있어요.",
                "끌어서 위쪽 날짜 칸에 놓으면 그날로 옮겨지고, 아래 보관함에 놓으면 보관함으로 들어가요.",
                "빈 곳을 옆으로 밀면 전날이나 다음 날로 넘어가요.",
            ),
        ),
        GuidePart(
            6,
            "주 · 일 시간표에서",
            listOf(
                "젤리를 꾹 누른 채 끌면 다른 시간이나 다른 날로 옮겨요.",
                "젤리 아래쪽 손잡이를 위아래로 끌면 길이가 바뀌어요.",
                "옆으로 밀면 앞뒤 주나 앞뒤 날로 넘어가요.",
                "주 화면에서 요일 칸을 누르면 그날의 일 화면이 열려요.",
            ),
        ),
        GuidePart(
            1,
            "완료와 보관함",
            listOf(
                "완료한 젤리는 색이 진해지고, 상자에서는 흰 테두리와 체크 표시가 생겨요.",
                "그날이 지나도록 끝내지 못한 젤리는 아래 보관함으로 모여요. 젤리의 ‘못 하면 보관함으로’를 끄면 그날에 ‘놓침’으로 남아요.",
                "보관함 젤리를 꾹 눌러 위쪽 날짜 칸이나 상자, 시간표로 끌어 놓으면 다시 일정이 돼요.",
                "보관함 젤리를 바로 완료하면 오늘 한 일로 기록돼요.",
            ),
        ),
        GuidePart(
            4,
            "반복 젤리",
            listOf(
                "젤리를 만들 때 ‘반복’에서 매일, 평일, 주말이나 요일을 고르면 그 요일마다 자동으로 깔려요.",
                "오른쪽 위 메뉴(⋮)의 ‘반복 젤리’에서 모아 보고, 고치거나 잠시 끌 수 있어요.",
                "반복 젤리의 하루치를 고치면 그날만 바뀌어요.",
            ),
        ),
        GuidePart(
            2,
            "기상 알람 (삼성 시계)",
            listOf(
                "상자 · 일 화면 위쪽 카드가 그날 첫 일과와 기상 알람을 함께 보여 줘요. 오늘 아침이 이미 지났으면 내일을 눌러 보세요.",
                "카드의 알람 버튼을 누르면 첫 일과 ${gapMin}분 전으로 삼성 시계에 알람을 추가해요. 설정에 따라 바뀌기 전 알람을 끄는 요청도 함께 보내요.",
                "반복 젤리에서 ‘기상 알람에 맞춰 시작’을 켜면 알람이 바뀔 때 그 젤리가 알람 ${gapMin}분 뒤로 따라 움직여요.",
                "알람은 휴대폰이 알려 주는 ‘다음 알람’ 하나만 읽을 수 있어요.",
            ),
        ),
        GuidePart(
            7,
            "공유 젤리와 모두",
            listOf(
                "‘공유 젤리’ 탭은 정한 사람과 함께 쓰는 달력이에요. 내 젤리는 이 휴대폰에만 있고, 공유 젤리 탭에 올린 젤리만 상대와 나눠요.",
                "처음에 ‘새 공유 달력 만들기’를 누른 뒤, 오른쪽 위 ⋯ 메뉴의 ‘상대 초대하기’로 초대 코드를 보내세요.",
                "상대가 아이폰을 쓰면 사파리에서 초대 링크를 열고 ‘홈 화면에 추가’를 누르면 앱처럼 쓸 수 있어요.",
                "‘달력 · 상자’ 버튼으로 한 달 달력과 하루 상자를 오가요. 공유 젤리는 두 사람 모두 바로 고칠 수 있고, 젤리를 눌러 메모를 남길 수 있어요.",
                "‘모두’ 탭은 그날의 내 젤리와 공유 젤리를 한 상자에 모아 보여 줘요. 동그라미 글자가 붙은 젤리가 공유 젤리이고, 그 글자는 올린 사람이에요.",
            ),
        ),
        GuidePart(
            8,
            "되돌리기, 백업, 글씨체",
            listOf(
                "젤리를 지우거나 보관함으로 보낸 직후 아래에 뜨는 ‘되돌리기’를 누르면 되돌릴 수 있어요.",
                "설정의 ‘데이터’에서 백업 파일을 저장하고 불러올 수 있어요.",
                "설정의 ‘화면’에서 글씨체를 ‘말랑’, ‘깔끔’, ‘휴대폰 글꼴’ 가운데 고를 수 있어요.",
            ),
        ),
    )
}

/** How the app works, one card per topic. Opens by itself on the first launch. */
@Composable
fun GuideScreen(settings: Settings, gapMin: Int, onBack: () -> Unit) {
    val colors = LocalJellyColors.current
    val type = LocalJellyType.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("사용법", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                keepWords("젤리 달력은 할 일 하나를 말랑한 젤리 하나로 보여 줘요. 아래 순서대로 한 번 훑어보세요."),
                color = colors.textSub,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            for (part in guideParts(settings, gapMin)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(boxColor(part.flavor)),
                        )
                        Text(
                            part.title,
                            color = colors.text,
                            fontSize = 18.sp,
                            fontFamily = type.display,
                            fontWeight = type.displayWeight,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    for (line in part.lines) {
                        Row(Modifier.padding(top = 6.dp)) {
                            Box(
                                Modifier
                                    .padding(top = 8.dp, end = 10.dp)
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(boxColor(part.flavor).copy(alpha = 0.9f)),
                            )
                            Text(
                                keepWords(line),
                                color = colors.text,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            JellyButton(
                "시작하기",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Text(
                keepWords("이 화면은 오른쪽 위 메뉴(⋮)나 설정에서 다시 볼 수 있어요."),
                color = colors.textSub,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}
