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
        "다 먹었어요: 젤리를 톡 눌러 열고 ‘✓ 다 먹었어요’를 누르세요. 두 번 톡이나 꾹 눌렀다 떼기로 끝내려면 설정에서 켜세요."
    } else {
        "$finish: ‘다 먹었어요’로 표시해요. 한 번 더 하면 다시 할 일로 돌아가요."
    }
    return listOf(
        GuidePart(
            5,
            "화면 고르기",
            listOf(
                "맨 위 ‘내 젤리 · 공유 젤리 · 모두’ 탭으로 무엇을 볼지 골라요.",
                "맨 아래 가운데 ‘상자 · 주 · 일 · 달’로 보는 방식을 바꿔요. 아래 왼쪽 ‘오늘’은 어디서든 오늘로 데려다주고, 아래 오른쪽 ‘+’는 어디서든 새 젤리를 만들어요.",
                "상자: 하루를 상자 하나로 보여 줘요. 그날 젤리가 시작 시간 순서대로 떨어져 쌓이고, 오래 걸리는 일일수록 젤리가 커요.",
                "주 · 일: 시간표로 보여 줘요. 달: 한 달을 한눈에 보여 주고, 하루에 할 젤리를 세 개까지 보여 줘요. 다 먹은 젤리는 달력 칸에서 빠지고, 아래 그날 목록에 접혀 있어요.",
                "일요일과 공휴일(설날, 추석, 개천절, 한글날, 대체공휴일 같은 쉬는 날)은 빨간색이에요. 한 주는 일요일부터 시작하고, 설정에서 월요일로 바꿀 수 있어요.",
            ),
        ),
        GuidePart(
            0,
            "새 젤리 빚기",
            listOf(
                "아래 오른쪽 ‘+’를 누르고 무엇을 할지, 맛(색), 길이, 언제를 정한 뒤 ‘젤리 담기’를 누르세요.",
                "이름에 ‘11시 미용실’, ‘오후 3시 반 회의’, ‘19:30 영화’처럼 시각을 쓰면 시간이 그 시각으로 맞춰져요. 오전·오후를 안 쓰면 1~6시는 오후, 7~11시는 오전으로 봐요. 이미 있는 젤리는 ‘제목대로’ 버튼으로 맞춰요.",
                "길이는 젤리를 오른쪽으로 쭉 당겨서 정해요. 두 시간까지는 10분씩, 그 뒤로는 30분씩 열두 시간까지 늘어나요.",
                "‘더 보기’에는 젤위로 고정, 시작 전 알람, 반복, 보관함 규칙, 메모가 있어요.",
                "주 · 일 시간표에서는 빈 곳을 꾹 누르면 그 시간으로 새 젤리가 빚어져요. 언제를 ‘보관함에 두기’로 고르면 날짜 없이 보관함에 넣어 둬요.",
            ),
        ),
        GuidePart(
            3,
            "상자에서 젤리 다루기",
            listOf(
                "톡: 젤리를 열어 이름, 시간, 길이를 고쳐요.",
                finishLine,
                "끌기: 젤리를 잡고 흔들 수 있어요. 위쪽 날짜 칸에 놓으면 그날로, 아래 보관함에 놓으면 보관함으로 옮겨요.",
                "빈 곳을 옆으로 밀면 전날이나 다음 날로 넘어가요.",
            ),
        ),
        GuidePart(
            9,
            "젤위로 고정",
            listOf(
                "젤 중요한 젤리는 젤리를 열고 ‘더 보기’의 ‘📌 젤위로 고정’을 켜세요. 상자 맨 위에 핀으로 꽂혀 그 자리에서 살랑살랑 흔들리고, 다른 젤리는 그 아래로 흘러내려요. 끌어당기면 쭉 늘어났다가 제자리로 돌아와요.",
                "고정은 하루에 세 개까지예요. 달력 칸에서도 고정한 젤리가 맨 위에 진한 색으로 보여요.",
            ),
        ),
        GuidePart(
            6,
            "주 · 일 시간표와 달력에서",
            listOf(
                "젤리를 꾹 누른 채 끌면 다른 시간이나 다른 날로 옮겨요. 달력에서는 다른 날 칸에 놓으면 그날로 옮겨요.",
                "젤리 아래쪽 손잡이를 위아래로 끌면 길이가 바뀌어요.",
                "옆으로 밀면 앞뒤 날, 주, 달로 넘어가요. 주 화면에서 날짜를 누르면 그날을 보여 주고, 달력에서 날짜를 누르면 그날 젤리가 아래에서 바로 올라와요. 거기서 젤리를 고치거나 새로 담을 수 있어요.",
            ),
        ),
        GuidePart(
            1,
            "다 먹은 젤리와 보관함",
            listOf(
                "다 먹은 젤리는 색이 진해지고, 상자에서는 흰 테두리와 체크 표시가 생겨요.",
                "그날이 지나도록 끝내지 못한 젤리는 아래 보관함으로 모여요. 젤리의 ‘못 하면 보관함으로’를 끄면 그날에 ‘놓침’으로 남아요.",
                "보관함 젤리를 꾹 눌러 날짜 칸이나 상자, 달력에 놓으면 ‘언제 먹을까요?’ 하고 물어요. 원래 하려던 시간, 그날 비어 있는 시간, 직접 고르기 가운데 하나를 고르세요. 시간표의 원하는 칸에 바로 놓아도 돼요.",
                "보관함 젤리를 바로 다 먹으면 오늘 한 일로 기록돼요.",
            ),
        ),
        GuidePart(
            4,
            "반복 젤리",
            listOf(
                "젤리를 만들 때 ‘더 보기’의 ‘반복’에서 매일, 평일, 주말이나 요일을 고르면 그 요일마다 자동으로 깔려요.",
                "오른쪽 위 메뉴(⋮)의 ‘반복 젤리’에서 모아 보고, 고치거나 잠시 끌 수 있어요.",
                "반복 젤리의 하루치를 고치면 그날만 바뀌어요.",
            ),
        ),
        GuidePart(
            2,
            "알람 (삼성 시계)",
            listOf(
                "상자 · 일 화면 위쪽 카드가 그날 첫 일과와 기상 알람을 함께 보여 줘요. 오늘 아침이 이미 지났으면 내일을 눌러 보세요.",
                "카드의 알람 버튼을 누르면 첫 일과 ${gapMin}분 전으로 삼성 시계에 알람을 추가해요. 설정에 따라 바뀌기 전 알람을 끄는 요청도 함께 보내요.",
                "젤리마다 ‘더 보기’의 ‘⏰ 시작 전 알람’으로 정각, 10분 전, 30분 전 알람을 맞출 수 있어요.",
                "알람 요청에는 날짜를 담을 수 없어서, 하루 넘게 남은 알람은 ‘날짜 골라 맞추기’로 맞춰요. 삼성 시계의 알람 추가 화면이 열리면 달력 아이콘으로 날짜를 고른 뒤 저장하면 돼요.",
                "반복 젤리에서 ‘기상 알람에 맞춰 시작’을 켜면 알람이 바뀔 때 그 젤리가 알람 ${gapMin}분 뒤로 따라 움직여요.",
            ),
        ),
        GuidePart(
            7,
            "공유 젤리와 모두",
            listOf(
                "‘공유 젤리’ 탭은 정한 사람들과 함께 쓰는 달력이에요. 내 젤리는 이 휴대폰에만 있고, 공유 달력에 올린 젤리만 함께 쓰는 사람과 나눠요.",
                "처음에 ‘새 공유 달력 만들기’를 누른 뒤, 오른쪽 위 ⋯ 메뉴의 ‘함께 쓸 사람 초대하기’로 초대 코드를 보내세요. 아이폰에서는 사파리로 초대 링크를 열면 돼요.",
                "공유 달력은 다섯 개까지 쓸 수 있어요. 제목 아래 달력 이름을 누르면 다른 달력으로 바꾸거나 달력을 더 만들 수 있어요.",
                "‘모두’ 탭은 내 젤리와 모든 공유 달력의 젤리를 한데 모아 보여 줘요. 공유 젤리는 올린 사람의 캐릭터를 입고 있거나, 올린 사람의 동그라미 글자가 붙어 있어요.",
                "공유 젤리의 이름, 시간, 메모는 달력에 들어온 사람의 기기에서만 열리도록 암호화돼요.",
            ),
        ),
        GuidePart(
            5,
            "내 캐릭터",
            listOf(
                "설정의 ‘내 캐릭터’에서 얼굴과 직업 옷을 골라요. 직업은 스무 가지이고, 직업마다 표정과 옷이 다른 두 가지가 있어요.",
                "공유 달력에서는 내가 올린 젤리와 내 동그라미가 이 모습이 돼요. 함께 쓰는 사람도 저마다 고른 캐릭터로 보여요.",
                "‘내 젤리에도 입히기’를 켜면 상자 속 내 젤리도 같은 캐릭터를 입어요. 큰 젤리는 얼굴까지, 작은 젤리는 모자만 보여요.",
            ),
        ),
        GuidePart(
            8,
            "되돌리기, 백업, 글씨체",
            listOf(
                "젤리를 지우거나 보관함으로 보낸 직후 아래에 뜨는 ‘되돌리기’를 누르면 되돌릴 수 있어요.",
                "설정의 ‘데이터’에서 백업 파일을 저장하고 불러올 수 있어요.",
                "설정의 ‘화면’에서 글씨체를 ‘동글’, ‘말랑’, ‘깔끔’, ‘통통’, ‘휴대폰 글꼴’ 가운데 고를 수 있어요. 공유 젤리 화면도 같은 글씨를 따라요.",
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
