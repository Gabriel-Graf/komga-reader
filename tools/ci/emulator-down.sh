#!/bin/bash
# Fährt den in CI gestarteten Emulator herunter (lässt einen extern/lokal laufenden in Ruhe,
# wenn keine PID-Datei existiert).
set -euo pipefail
readonly SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${HOME}/Android/Sdk}}"
readonly ADB="${SDK}/platform-tools/adb"
err() { echo "[emulator-down] $*" >&2; }

if [[ -f /tmp/ci-emulator.pid ]]; then
  serial="$("${ADB}" devices | awk '/^emulator-/{print $1; exit}')"
  if [[ -n "${serial}" ]]; then
    "${ADB}" -s "${serial}" emu kill 2>/dev/null || true
  fi
  kill "$(cat /tmp/ci-emulator.pid)" 2>/dev/null || true
  rm -f /tmp/ci-emulator.pid
  err "Emulator gestoppt"
else
  err "keine CI-PID — nichts zu stoppen"
fi
