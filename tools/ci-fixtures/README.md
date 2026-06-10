# CI-Fixtures — Komga-Integrationstest-Backends

Fährt reproduzierbar 3 Komga-Instanzen mit deterministischem Test-Content hoch.

## Schnellstart (lokal)

    ./up.sh        # compose up + seed + verify; druckt pro Instanz baseUrl + API-Key
    ./down.sh      # stoppt (config-Volumes bleiben; -v zum Wipen)

## Topologie

| Instanz | Port  | Libraries                     |
|---------|-------|-------------------------------|
| komga-a | 25701 | Manga (cbz) + Novels-A (epub) |
| komga-b | 25702 | Webtoon (cbz) + Novels-B (epub, disjunkt) |
| komga-c | 25703 | Spiegel von A (n-gleiche-Server-Test) |

Vom Android-Emulator erreichbar über `http://10.0.2.2:<port>/api/v1/`.

## Content-Tiers

- **Tier 1** (`content/`, committet, CC0): selbst generiert via `gen-fixtures.py`.
  Trägt die Masse der Tests, CI-portabel ohne NAS.
- **Tier 2** (runner-lokal, NIE committet): echter NAS-Subset via `freeze-from-nas.sh`,
  nur für Render-Smoke. Siehe `PROVENANCE.md`.

## Determinismus

`manifest.json` hält erwartete Serien/Counts/Typen + SHAs. `verify.sh` asserted dagegen.
Tier-1-Content ist committet → jeder Lauf identisch.

## CI

Die GitLab-Pipeline (`.gitlab-ci.yml` im Repo-Root) fährt diese Fixtures im `integration`-Stage
automatisch hoch (`up.sh`) und nach den Tests wieder runter (`down.sh`). Sie braucht einen
shell-executor-Runner mit Tag `android-kvm` (Docker + /dev/kvm + Android-SDK + AVD `eink_test`).
Siehe `docs/superpowers/plans/2026-06-10-integration-ci-pipeline.md`.
