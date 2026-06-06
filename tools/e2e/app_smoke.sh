#!/usr/bin/env bash
set -euo pipefail
ADB=/usr/bin/adb
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=com.komgareader.app

echo "[1/5] Warte auf Emulator..."
"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
echo "[2/5] Boot abgeschlossen."

echo "[3/5] Installiere APK..."
"$ADB" install -r -t "$APK"

echo "[4/5] Starte App + leere Logcat..."
"$ADB" logcat -c
"$ADB" shell am start -W -n "$PKG/.MainActivity"
sleep 4

echo "[5/5] Pruefe auf Crash..."
if "$ADB" logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime.*$PKG" | grep -q .; then
  echo "FAIL: Crash im Logcat"; "$ADB" logcat -d | grep -A20 "FATAL EXCEPTION" | head -30; exit 1
fi
PID="$("$ADB" shell pidof "$PKG" | tr -d '\r' || true)"
if [ -z "$PID" ]; then echo "FAIL: Prozess laeuft nicht"; exit 1; fi
"$ADB" exec-out screencap -p > /tmp/app_smoke.png || true
echo "PASS: App laeuft (PID $PID), Screenshot /tmp/app_smoke.png"
