# Integrationstest — Plan CI: GitLab-Pipeline (shell-executor, KVM-Emulator)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development oder executing-plans. Checkbox-Steps.

**Goal:** `.gitlab-ci.yml`, die die lokale Suite in echter GitLab-CI fährt: Build → JVM-Unit → Integration (Komga-Fixtures hoch + headless KVM-Emulator + alle `ci.*`-Tests + Teardown). Läuft auf einem **shell-executor-Runner** auf der bare-metal-Box (Docker, `/dev/kvm`, Android-SDK, NAS lokal vorhanden).

**Architecture:** Der Integration-Job läuft als shell-executor (Tag `android-kvm`) direkt auf dem Host — voller Docker-Daemon-Zugriff (Komga-Fixtures via `up.sh` mit echten Host-Pfaden, kein dind/Socket-Problem), `/dev/kvm` für den Emulator, Repo als echter Host-Checkout. Die Job-Skripte sind exakt die lokal grünen Befehle, gekapselt in zwei Emulator-Helfer.

**Tech Stack:** GitLab CI (shell-executor), Android cmdline-tools/emulator (KVM, headless `-no-window -gpu swiftshader_indirect`), Gradle, die `tools/ci-fixtures`-Orchestrierung.

**Verifikations-Grenze (wichtig):** Der echte CI-Lauf braucht `git push` + den Runner — **nicht** von hier ausführbar. Lokal verifizierbar: yaml-Syntax, `shellcheck` der Helfer, und dass die gekapselten Befehle (up.sh, Emulator-Boot, connectedAndroidTest) lokal grün sind (bereits bewiesen). Der End-to-End-CI-Beweis ist der erste Pipeline-Lauf nach dem Push.

---

## Runner-Voraussetzungen (vom Nutzer einmalig, root/Host)

Diese Schritte macht der Nutzer auf der Box (nicht der Agent — root/Registrierung):

1. **Shell-Executor-Runner registrieren**, Tag `android-kvm`:
   ```bash
   sudo gitlab-runner register --non-interactive --executor shell \
     --url <GITLAB_URL> --registration-token <TOKEN> \
     --description "android-kvm-shell" --tag-list "android-kvm"
   ```
2. **Runner-User braucht Zugriff auf:** Docker (`usermod -aG docker gitlab-runner`), KVM (`usermod -aG kvm gitlab-runner`), und ein Android-SDK inkl. emulator + die AVD `eink_test` (Boox-Geometrie). Entweder das SDK des Dev-Users teilen (`ANDROID_HOME` setzen) oder fürs Runner-Home installieren. NAS unter `/mnt/nas` gemountet (für Tier-2, optional).
3. `/dev/kvm` nutzbar für den Runner-User (Gruppe `kvm`, gid prüfen).

(Der docker-executor-`devices=["/dev/kvm"]`-Fix aus der Spec entfällt für diesen Weg — er galt nur, wenn der Emulator IM docker-Job liefe. Shell-executor nutzt `/dev/kvm` direkt.)

---

## Task 1: Emulator-Helfer (headless boot/teardown)

**Files:**
- Create: `tools/ci/emulator-up.sh`
- Create: `tools/ci/emulator-down.sh`

- [ ] **Step 1: emulator-up.sh schreiben**

```bash
#!/bin/bash
# Bootet die AVD headless mit KVM und wartet auf vollständigen Boot. Idempotent: läuft schon
# ein Emulator, wird er wiederverwendet. ANDROID_HOME/CI_AVD_NAME überschreibbar.
set -euo pipefail

readonly AVD="${CI_AVD_NAME:-eink_test}"
readonly SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${HOME}/Android/Sdk}}"
readonly EMU="${SDK}/emulator/emulator"
readonly ADB="${SDK}/platform-tools/adb"
err() { echo "[emulator-up] $*" >&2; }

"${ADB}" start-server

if "${ADB}" devices | grep -q '^emulator-'; then
  err "Emulator läuft bereits — wiederverwenden"
else
  err "starte headless Emulator '${AVD}'"
  nohup "${EMU}" -avd "${AVD}" -no-window -no-audio -no-boot-anim \
    -gpu swiftshader_indirect -no-snapshot -read-only \
    >/tmp/ci-emulator.log 2>&1 &
  echo $! > /tmp/ci-emulator.pid
fi

"${ADB}" wait-for-device
booted=""
for _i in $(seq 1 150); do
  booted="$("${ADB}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if [[ "${booted}" == "1" ]]; then break; fi
  sleep 2
done
if [[ "${booted}" != "1" ]]; then err "Boot-Timeout"; exit 1; fi
"${ADB}" shell input keyevent 82 >/dev/null 2>&1 || true   # Lockscreen weg
err "Emulator bereit"
```

- [ ] **Step 2: emulator-down.sh schreiben**

```bash
#!/bin/bash
# Fährt den in CI gestarteten Emulator herunter (lässt einen extern/lokal laufenden in Ruhe,
# wenn keine PID-Datei existiert).
set -euo pipefail
readonly SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${HOME}/Android/Sdk}}"
readonly ADB="${SDK}/platform-tools/adb"
err() { echo "[emulator-down] $*" >&2; }

if [[ -f /tmp/ci-emulator.pid ]]; then
  "${ADB}" -s "$("${ADB}" devices | awk '/^emulator-/{print $1; exit}')" emu kill 2>/dev/null || true
  kill "$(cat /tmp/ci-emulator.pid)" 2>/dev/null || true
  rm -f /tmp/ci-emulator.pid
  err "Emulator gestoppt"
else
  err "keine CI-PID — nichts zu stoppen"
fi
```

- [ ] **Step 3: Ausführbar + ShellCheck**

Run: `cd tools/ci && chmod +x emulator-up.sh emulator-down.sh && shellcheck emulator-up.sh emulator-down.sh && echo CLEAN`
Expected: `CLEAN`.

- [ ] **Step 4: Lokaler Smoke (optional, da Emulator hier schon läuft)**

Run: `tools/ci/emulator-up.sh` → muss „Emulator läuft bereits — wiederverwenden" + „Emulator bereit" melden (der lokale emulator-5554 wird erkannt, kein zweiter gestartet, kein down).
Expected: exit 0, kein neuer Emulator. (emulator-down.sh hier NICHT laufen lassen — würde den lokalen killen; es gibt eh keine PID-Datei, also wäre es ohnehin no-op.)

- [ ] **Step 5: Commit**

```bash
git add tools/ci/emulator-up.sh tools/ci/emulator-down.sh
git commit -m "feat(ci): headless KVM-Emulator-Boot/Teardown-Helfer"
```

---

## Task 2: .gitlab-ci.yml

**Files:**
- Create: `.gitlab-ci.yml`

- [ ] **Step 1: Pipeline schreiben**

```yaml
# GitLab-CI für die Komga-Reader-Integrationstest-Suite.
# build/unit laufen auf jedem Runner; integration braucht einen shell-executor-Runner mit
# Docker + /dev/kvm + Android-SDK (Tag android-kvm) — siehe Plan CI / Runner-Voraussetzungen.
stages:
  - build
  - unit
  - integration

variables:
  GRADLE_OPTS: "-Dorg.gradle.daemon=false -Dorg.gradle.caching=true"

build:
  stage: build
  tags: [android-kvm]
  script:
    - ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
  artifacts:
    paths:
      - app/build/outputs/apk/debug/
    expire_in: 1 day

unit:
  stage: unit
  tags: [android-kvm]
  script:
    - ./gradlew test
  artifacts:
    when: always
    paths:
      - "**/build/reports/tests/"
    expire_in: 1 week

integration:
  stage: integration
  tags: [android-kvm]
  before_script:
    - tools/ci-fixtures/up.sh          # Komga-Fixtures (Cache-Hit schnell)
    - tools/ci/emulator-up.sh          # headless KVM-Emulator booten
  script:
    # In CI ist genau EIN Emulator und keine physische Boox → kein ANDROID_SERIAL nötig.
    - ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.komgareader.app.ci
  after_script:
    - tools/ci/emulator-down.sh
    - tools/ci-fixtures/down.sh
  artifacts:
    when: always
    paths:
      - app/build/reports/androidTests/
    expire_in: 1 week
```

- [ ] **Step 2: YAML-Syntax validieren**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.gitlab-ci.yml')); print('YAML OK')"`
Expected: `YAML OK`.

- [ ] **Step 3: Commit**

```bash
git add .gitlab-ci.yml
git commit -m "feat(ci): GitLab-Pipeline — build/unit/integration (shell-executor, KVM-Emulator, Komga-Fixtures)"
```

---

## Task 3: README-Hinweis + Spec-Update

**Files:**
- Modify: `tools/ci-fixtures/README.md` (CI-Abschnitt)
- Modify: `docs/superpowers/specs/2026-06-10-integration-test-suite-design.md` (§8 auf Ist-Stand)

- [ ] **Step 1: README CI-Abschnitt**

Ans Ende von `tools/ci-fixtures/README.md` ergänzen:
```markdown
## CI

Die GitLab-Pipeline (`.gitlab-ci.yml` im Repo-Root) fährt diese Fixtures im `integration`-Stage
automatisch hoch (`up.sh`) und nach den Tests wieder runter (`down.sh`). Sie braucht einen
shell-executor-Runner mit Tag `android-kvm` (Docker + /dev/kvm + Android-SDK + AVD `eink_test`).
Siehe `docs/superpowers/plans/2026-06-10-integration-ci-pipeline.md`.
```

- [ ] **Step 2: Spec §8 auf Ist-Stand**

In §8 vermerken: shell-executor statt docker-executor+dind (relative Volume-Mounts + Emulator-im-Container
vermieden); der `devices=["/dev/kvm"]`-Fix gilt nur für den docker-Weg und entfällt hier.

- [ ] **Step 3: Commit**

```bash
git add tools/ci-fixtures/README.md docs/superpowers/specs/2026-06-10-integration-test-suite-design.md
git commit -m "docs(ci): README + Spec §8 auf shell-executor-Pipeline aktualisiert"
```

---

## Task 4: Push + erster CI-Lauf (Nutzer)

- [ ] **Step 1:** Runner-Voraussetzungen erfüllen (siehe oben).
- [ ] **Step 2:** Branch pushen, Pipeline beobachten. `build`+`unit` müssen auf jedem Runner grün sein; `integration` braucht den `android-kvm`-Runner.
- [ ] **Step 3:** Bei rotem `integration`: Job-Log + die Emulator-/Fixture-Logs prüfen. Häufig: AVD für Runner-User nicht vorhanden, `/dev/kvm`-Permission, SDK-Pfad (`ANDROID_HOME` im Runner-Env setzen).

---

## Self-Review (Plan-Autor)

- **Fork umgesetzt:** shell-executor (least friction; relative Volume-Mounts + Emulator funktionieren wie lokal). docker-executor+dind bewusst verworfen.
- **Gekapselte Befehle sind lokal grün:** `up.sh`/`down.sh` (Plan 1), `connectedDebugAndroidTest …package=ci` (Plan 2-4). CI fügt nur Emulator-Boot/Teardown hinzu.
- **Verifikations-Grenze offen kommuniziert:** echter CI-Lauf = Push + Runner, nicht vom Agent ausführbar. Lokal: yaml-lint + shellcheck + die Tatsache, dass alle Job-Schritte einzeln bewiesen sind.
- **Idempotenter Emulator-Helfer:** erkennt einen laufenden Emulator (lokaler emulator-5554 wird nicht doppelt gestartet/gekillt).

## Danach offen

- Block D17 (Sammlungen-Push/Pull), Block H (Plugins `[pending]`), C10-UI — alle dokumentiert in Spec §9b.
- Optional: separate Caches (Gradle) im Runner, Parallelisierung build/unit auf einem leichten docker-Runner.
