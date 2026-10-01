#!/usr/bin/env bash
# Installs the APK on a running emulator, opens the app, walks through the main screens and
# records screenshots and the log. Fails when the app crashes or is not running at the end.
set -uo pipefail

apk="$1"
out="$2"
pkg="io.github.ckdgus6068.jellycalendar"
mkdir -p "$out"

shot() {
    adb exec-out screencap -p > "$out/$1.png"
}

# Taps the first on-screen element whose text is exactly "$1".
tap_text() {
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb exec-out cat /sdcard/ui.xml > "$out/ui.xml"
    local bounds
    bounds=$(grep -o "text=\"$1\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" "$out/ui.xml" | head -n 1 |
        sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')
    if [ -z "$bounds" ]; then
        echo "::warning::no element with text $1"
        return 1
    fi
    set -- $bounds
    adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

alive() {
    [ -n "$(adb shell pidof "$pkg" | tr -d '\r')" ]
}

adb install -r "$apk" || { echo "::error::install failed"; exit 1; }
adb shell dumpsys package "$pkg" | grep -E "versionCode|versionName" | head -n 2
adb logcat -c
adb shell am start -W -n "$pkg/.MainActivity"
sleep 12
shot 1-first-launch
alive || echo "::error::app died right after launch"

# The how-to screen opens on the first launch; back returns to the calendar's box view.
adb shell input keyevent KEYCODE_BACK
sleep 5
shot 2-box

tap_text "주" && sleep 4 && shot 3-week
tap_text "일" && sleep 4 && shot 4-day
tap_text "상자" && sleep 6 && shot 5-box-again
tap_text "공유 젤리" && sleep 12 && shot 5b-shared
tap_text "모두" && sleep 8 && shot 5c-all
tap_text "내 젤리" && sleep 3

# The shared page reports the size it sees. A web view laid out without a height (CSS vh of 0)
# squashes the page's bottom sheets into a sliver.
viewport=$(adb logcat -d | grep -o 'jelly-share viewport [^"]*' | tail -n 1)
echo "shared page: ${viewport:-did not report its viewport}"
flat_page=0
if echo "$viewport" | grep -Eq 'vh100=0$'; then
    echo "::error::the shared page sees a zero viewport height"
    flat_page=1
fi

# Cold start again, now with saved data and the guide already seen.
adb shell am force-stop "$pkg"
adb shell am start -W -n "$pkg/.MainActivity"
sleep 10
shot 6-second-launch

adb logcat -d > "$out/logcat.txt"
adb logcat -d -b crash > "$out/crash.txt" 2>/dev/null || true

status=$flat_page
grep -E 'CONSOLE.*(Uncaught|TypeError|ReferenceError)' "$out/logcat.txt" | head -n 20
if grep -q "FATAL EXCEPTION" "$out/logcat.txt" || [ -s "$out/crash.txt" ]; then
    echo "::error::the app crashed"
    grep -n -A 40 "FATAL EXCEPTION" "$out/logcat.txt" | head -n 160
    cat "$out/crash.txt" | head -n 160
    status=1
fi
if ! alive; then
    echo "::error::the app is not running at the end"
    status=1
fi
grep -E "AndroidRuntime|$pkg" "$out/logcat.txt" | tail -n 60
exit $status
