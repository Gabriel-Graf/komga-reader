#!/bin/bash
# Bootet die AVD headless mit KVM und wartet auf vollständigen Boot. Idempotent: läuft schon
# ein Emulator, wird er wiederverwendet. Spricht den Emulator gezielt per Serial an (-s), damit
# es auch mit weiteren angeschlossenen Geräten (z.B. physische Boox) funktioniert.
# ANDROID_HOME/CI_AVD_NAME überschreibbar.
set -euo pipefail

readonly AVD="${CI_AVD_NAME:-eink_test}"
readonly SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${HOME}/Android/Sdk}}"
readonly EMU="${SDK}/emulator/emulator"
readonly ADB="${SDK}/platform-tools/adb"
err() { echo "[emulator-up] $*" >&2; }

emulator_serial() { "${ADB}" devices | awk '/^emulator-/{print $1; exit}'; }

"${ADB}" start-server

if [[ -n "$(emulator_serial)" ]]; then
  err "Emulator läuft bereits — wiederverwenden"
else
  err "starte headless Emulator '${AVD}'"
  nohup "${EMU}" -avd "${AVD}" -no-window -no-audio -no-boot-anim \
    -gpu swiftshader_indirect -no-snapshot -read-only \
    >/tmp/ci-emulator.log 2>&1 &
  echo $! > /tmp/ci-emulator.pid
fi

# Auf das Erscheinen der Emulator-Serial warten (frisch gestarteter Emulator braucht kurz).
serial=""
for _i in $(seq 1 60); do
  serial="$(emulator_serial)"
  if [[ -n "${serial}" ]]; then break; fi
  sleep 2
done
if [[ -z "${serial}" ]]; then err "kein Emulator gefunden"; exit 1; fi
err "Emulator-Serial: ${serial}"

"${ADB}" -s "${serial}" wait-for-device
booted=""
for _i in $(seq 1 150); do
  booted="$("${ADB}" -s "${serial}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if [[ "${booted}" == "1" ]]; then break; fi
  sleep 2
done
if [[ "${booted}" != "1" ]]; then err "Boot-Timeout"; exit 1; fi
"${ADB}" -s "${serial}" shell input keyevent 82 >/dev/null 2>&1 || true   # Lockscreen weg
err "Emulator bereit (${serial})"
