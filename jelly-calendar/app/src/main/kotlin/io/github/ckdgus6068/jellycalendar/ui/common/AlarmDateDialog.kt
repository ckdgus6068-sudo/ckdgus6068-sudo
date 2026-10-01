package io.github.ckdgus6068.jellycalendar.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.github.ckdgus6068.jellycalendar.ui.hm
import io.github.ckdgus6068.jellycalendar.ui.keepWords
import io.github.ckdgus6068.jellycalendar.ui.shortDate
import java.time.LocalDate

/**
 * Before Samsung Clock is opened for an alarm on a later day: what to do there. Its new-alarm
 * screen gets the time but not the date, so the person picks [date] with the calendar icon.
 */
@Composable
fun AlarmDateDialog(date: LocalDate, minute: Int, onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${shortDate(date)} ${hm(minute)} 알람") },
        text = {
            Text(
                keepWords("알람 요청에는 날짜를 담을 수 없어서, 삼성 시계의 알람 추가 화면을 열어 드릴게요.") +
                    "\n\n" +
                    keepWords("1. 시각이 ${hm(minute)}인지 확인하고") + "\n" +
                    keepWords("2. 달력 아이콘을 눌러 ${date.monthValue}월 ${date.dayOfMonth}일을 고른 다음") + "\n" +
                    keepWords("3. ‘저장’을 눌러 주세요."),
            )
        },
        confirmButton = {
            TextButton(onClick = onOpen) { Text("삼성 시계 열기") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}
