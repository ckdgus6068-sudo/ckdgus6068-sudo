package io.github.ckdgus6068.jellycalendar.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TitleTimeTest {

    // The same sentences as the shared page's test (share/test/titletime-cases.json).
    private val cases = listOf(
        "11시 미용실" to TitleTime.Said(660, "11시"),
        "오후 3시 반 회의" to TitleTime.Said(930, "오후 3시 반"),
        "저녁 7시 30분 영화" to TitleTime.Said(1170, "저녁 7시 30분"),
        "3시반 치과" to TitleTime.Said(930, "3시반"),
        "2시30분 회의" to TitleTime.Said(870, "2시30분"),
        "9시 출근" to TitleTime.Said(540, "9시"),
        "6시 퇴근" to TitleTime.Said(1080, "6시"),
        "5시 기상" to TitleTime.Said(300, "5시"),
        "저녁 약속 7시" to TitleTime.Said(1140, "7시"),
        "아침 러닝 6시" to TitleTime.Said(360, "6시"),
        "19:30 영화" to TitleTime.Said(1170, "19:30"),
        "오후 2:15 병원" to TitleTime.Said(855, "오후 2:15"),
        "1시간 공부" to null,
        "10시간 금식" to null,
        "점심 12시" to TitleTime.Said(720, "점심 12시"),
        "12시 점심" to TitleTime.Said(720, "12시"),
        "낮 2시 산책" to TitleTime.Said(840, "낮 2시"),
        "오전 12시" to TitleTime.Said(0, "오전 12시"),
        "밤 12시" to null,
        "밤 11시 통화" to TitleTime.Said(1380, "밤 11시"),
        "새벽 4시 출발" to TitleTime.Said(240, "새벽 4시"),
        "15시 회의" to TitleTime.Said(900, "15시"),
        "24시 편의점" to null,
        "10월 3일 11시 미용실" to TitleTime.Said(660, "11시"),
        "3시~5시 회의" to TitleTime.Said(900, "3시"),
        "123시" to null,
        "미용실" to null,
        "" to null,
        "엄마랑 통화 8시" to TitleTime.Said(480, "8시"),
        "회의 오후 3시" to TitleTime.Said(900, "오후 3시"),
    )

    @Test
    fun readsTimesTheWayTheyAreWritten() {
        for ((title, said) in cases) assertEquals(said, TitleTime.find(title), title)
    }
}
