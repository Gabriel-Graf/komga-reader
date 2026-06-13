---
name: komga-doc-sync
description: Use when finishing or changing a feature, seam, region slot, pack, source capability, plugin category, device-behaviour, or any architecture-affecting change in the Komga-Reader — before you commit. Triggers whenever code you touched may have made CLAUDE.md, .claude/rules/*.md, docs/superpowers/specs, or the English community docs (README, docs/ARCHITECTURE.md, docs/PROJECT-STATUS.md) stale. Symptoms: "ship it", "commit and we're done", a new slot/source/pack, a renamed/removed type, a changed module boundary.
---

# Komga Doc Sync

## Overview

This project carries a large, **hand-synced** knowledge base (`CLAUDE.md`, `.claude/rules/*.md`,
~38 `docs/superpowers/specs|plans/`) **plus** English community docs (`README.md`,
`docs/ARCHITECTURE.md`, `docs/PROJECT-STATUS.md`, `CONTRIBUTING.md`). Prose drifts from code — it
already has (the roadmap claimed the guided-view UI was missing after it shipped; the seams rule
once listed `Viewer`/`RefreshScheduler` as real before they existed).

**Core rule:** when you build or change a seam/component, update the affected docs **in the same
commit** as the code. Separate **Soll** (intended) from **Ist** (actual). Never document a type as
real that `grep` can't find. The code is the source of truth.

This is the binding `docs-match-code` discipline made invokable — and it **extends it to the
English docs**, which no older rule covers.

## When this fires

You changed any of: a Naht-A/B seam · a region slot or the `UiSlotPack`/`ResolvedSlots` set · a
theme/shell/UI pack or `ShellNavStyle` · a `MediaSource`/`BrowsableSource` capability · a
`PluginCategory`/ABI version · `DisplayMode`/`DisplayBehavior` · a `ViewerType`/reader · a module
boundary · or renamed/removed/added any architecture-level type. Also fires on "commit it / ship
it / we're done" after such work.

## What to update (decide by what changed)

| You changed… | Update, same commit |
|---|---|
| a seam, slot, pack, source/plugin capability | `.claude/rules/architecture-seams.md` (the matching section + the `UiSlotPack`/`ResolvedSlots` signatures) |
| a goal-level capability (new slot built, Kaleido, plugin type) | `.claude/rules/big-picture-and-goals.md` (the `ui-modularity` / goal tables, "Gebaut vs Noch offen" lists) |
| the module set or top-level invariants | `CLAUDE.md` (project) — module table + the seam summary |
| source-agnostic wiring, a new metadatum/source | `.claude/rules/source-agnostic-integration.md` / `source-extensibility.md` |
| device motion/accent behaviour | `.claude/rules/eink-design-language.md` / `animation-gating.md` |
| **anything a newcomer sees** (feature, module, build step, capability scope) | **`README.md` (feature matrix/module map), `docs/ARCHITECTURE.md`, `docs/PROJECT-STATUS.md`** — the English docs rot silently because no older rule lists them |
| a design decision/rationale | the matching `docs/superpowers/specs/` (add Soll vs Ist, don't rewrite history) |

If a change makes a "later / YAGNI / not yet built" note false, **move it to built** — stale
"not done" claims mislead the next agent worst of all.

## Verify before commit

1. `grep` every type/name you newly wrote into a doc — if grep can't find it, the doc lies. Fix it.
2. `git diff --stat` should show doc files alongside the code files. **Code-only diff for an
   architecture change = this skill was skipped.**
3. English docs: did the change alter what a newcomer would read? If yes and `README`/
   `ARCHITECTURE`/`PROJECT-STATUS` aren't in the diff, you're not done.

## Rationalizations — STOP

| Excuse | Reality |
|---|---|
| "Docs are a follow-up commit." | Same-commit or it never happens. The next agent reads stale docs in between. |
| "The German rules cover it; English docs are optional." | The English docs are the community's entry point and have **no** sync rule — they rot first. Update them. |
| "It's a small additive change." | A new slot/source/category is exactly what the tables enumerate. Additive = the tables are now wrong. |
| "Maintainer said ship it." | "Ship it" authorizes the commit, not skipping the docs that belong *in* it. |
| "I'll just describe it in the PR." | The PR isn't the doc the next reader greps. |

## Red flags

- `git diff --stat` shows only `.kt` files for an architecture-level change.
- You wrote a type name into a doc you haven't `grep`-verified.
- A doc still says a now-built thing is "later / YAGNI / not yet".
- You updated `.claude/rules/` but not the English `README`/`ARCHITECTURE`/`PROJECT-STATUS`.

All of these mean: update the docs **now**, in this commit, before you move on.
