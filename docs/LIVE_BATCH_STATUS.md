# Monogatari — Live batch status

Last observed: 2026-10-12 18:08 CDT. This is an observation, not an exact timeout timestamp.

## DONE (verified full CI and merged)
- Steps 1-5 except the required install/backup smoke checks; application identity and build setup exist.
- Steps 8-32 have various implementation stages in code and prior cumulative CI builds, but NOT every guide's device-based "Done when" condition has been checked.
- The NewPipe-backed PeerTube and SoundCloud adapters at commit 58fd81b3c passed CI run 38314916790 (debug+unsigned release) and were fast-forward merged to devel. Postmerge devel CI: 38320483011 in progress at checkpoint.
- Earlier YouTube playlists/channels passed CI at b014e1b93, but pagination beyond first feed page and Android smoke tests remain.

## IN PROGRESS (pushed, unmerged)
- Step 32 source reliability: branch batch-32f-source-reliability commit e014419f8. Pure NewPipeSearchPlan selects defaults by service (YouTube BL drama, PeerTube animation, SoundCloud audio drama), restricts channel/playlist search modes to YouTube, and isolates pagination keys by source/mode/query. Three regression tests. CI run 38320551815 in progress. Do not merge until green.
- PeerTube/SoundCloud live playback is unverified on an actual Android device.
- Step 32 YouTube channel/playlist full pagination, RSS tracking and graphical chips/filter UI remain unimplemented.
- Step 30 OPDS PDF/CBZ acquisition fallback and source device smoke tests remain unfinished.
- Guide Step 26 optional reading-list import is not implemented.

## BLOCKED / user-dependent
- Step 6 signed production release requires a keystore and configured GitHub Actions secrets.
- Step 7 external Google developer verification requires user action.
- Device testing of JS manga/novels/videos, source reading/playback, backup migration, screen-off controls remains outstanding.

## Master guide accounting
- 75 total numbered guide steps. Implementation has progressed through Step 32 (the first 32 numbered slots) but steps 6 and 7 aren't completed, and several other 1-32 guide steps have unmet acceptance checks. DO NOT report 32/75 fully completed.
- Steps 33-75 not yet built or acceptance-tested. A precise count of fully accepted steps cannot be certified without device-test evidence.
- Docs/PROGRESS.md is stale and must not override this checkpoint.

## NEXT ACTION — TUNNEL FIRST
1. Check runs 38320551815 and 38320483011. Repair CI failures, merge only green code into devel, verify remote SHA.
2. Continue Step 32 pagination/RSS and graphical video filters or proceed to Step 33 after recording unmet prerequisites.
3. Preserve untracked .release-temp/ and source-audit/. ALL GitHub edits exclusively via Tunnel to Surface.
4. End each batch with DONE / IN PROGRESS / BLOCKED and CI links.
