# Monogatari master-step audit — 2026-10-13

The canonical guide is `docs/MONOGATARI_MASTER_STEPS.md`. It numbers **1–74**.
Steps 62–73 appear as bullets in Stage M; there is **no Step 75**.

## Exclusive status categories — 74 total

| Classification | Steps | Count |
| --- | --- | ---: |
| Confirmed completed in the historical progress records | 1–2 | **2** |
| Implemented and CI-verifiable, but device acceptance remains unproven | 3–5 | **3** |
| Started/touched; still partial, research-only or not fully acceptance-tested | 8–32 and 34–72 | **64** |
| Not implemented / blocked / intentionally deferred / not executed | 6–7, 33, 73–74 | **5** |
| **Total** | **1–74** | **74** |

**69 of 74** have been started in some form (2 + 3 + 64). Of those, **2 are confirmed complete at the stated acceptance level**, 3 have implementation/CI milestones but still require physical-device checks, and **64 remain in progress or only researched**. The five outstanding untouched/blocked/deferred steps are not equivalent: 6 requires release signing material; 7 needs external developer verification; 33 requires a verified Wattpad integration; 73 is optional and consciously deferred; 74 is the final release checklist, not yet executed.

## Meaning of 'started'
This is a development-progress count, **not 69 functional features**. "Started" includes requirements investigated and documented without operational code. Explicit research-only examples include 52 (compact grid), 54 (Kototoro translation), 59 (missing JS helpers) and 61 (Dart-only source rewrites). The code and source-batch ledgers show deeper implementation across 8–32, 34–51 and many parts of 53–72, but external integrations and Android behavior remain unverified.

## Evidence / caveats
- `docs/PROGRESS.md` documents completed Steps 1–2 and CI milestones for 3–5, but was not fully maintained after October 12.
- Historical commit `8d3954e36` documents security/search Step 8–16 work.
- `docs/LIVE_BATCH_STATUS.md` at historical commit `97bf583b7` documents integrations through NewPipe, PeerTube and SoundCloud, with device acceptance pending.
- `docs/BATCH_v012_STATUS.md` records Stage G source health/presets and Steps 36–38 as partial.
- `docs/OVERNIGHT_INTEGRATION_v013.md` documents Steps 37–43 with partial sync and source restoration.
- `docs/BATCH_v013_EIGHT_STEP_STATUS.md` and `docs/BATCH_v014_STEPS_52_56.md`, `STEPS_57_61.md`, `STEPS_62_66.md`, and `STEPS_67_74.md` record later partial/research items.
- The newest app branch may pass JVM/debug/R8 in CI; **that is not device acceptance**, production signing, secure two-device synchronization or successful backup/import tests.

## Next acceptance priorities
1. Finish and validate database migration, source and backup restore, user libraries and readers on an actual Android device.
2. Implement remaining key functionality rather than count research-only steps as finished (offline translation, foreign import, protected private categories, media sources, automatic sync).
3. Close out Part 3 integration, notices, consistent UI, and host-function diagnostics.
4. Execute Part 4 full regression tests before marking more steps accepted, then Part 5 signed release checklist.
