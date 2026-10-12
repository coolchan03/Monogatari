# Monogatari progress
Last observed 2026-10-12 14:37:59 CDT.

- Steps 1-2: done.
- Step 3: CI debug and unsigned R8 build verified, run 38289292970; device smoke test pending.
- Steps 4-5: CI verified, run 38292011053; merged into devel at e497d88de. Device backup/restore pending.
- Step 6: signing credentials/keystore not set, signed release pending.
- Step 7: developer verification external/manual pending.
- Steps 8-16: cumulative implementation on batch-15-16-search-quality commit 68cceeac1. Latest CI 38297511275 was in progress at last check. Previous intermediate failures were patched, but no cumulative pass verified.
- Step 8: 15 repo defaults stored in GitHub secret; BuildConfig and confirmation-based setup link.
- Step 9: JS scoped cookies and tests.
- Step 10: JS source SHA-256/version checks and tests; extension index SHA-256 GitHub Action still pending.
- Step 11: host-scoped TLS bypass; testing pending.
- Step 12: salted PBKDF2 PIN, legacy MD5 migration; tests, compile fixes pushed.
- Step 13: content/privacy defaults, initial 18+ question.
- Step 14: diagnostics under Advanced; healthy first-run check silent.
- Steps 15-16: pinned-only search UI, retry fixes, concurrency 8 and BL/GL synonyms/tests.
- Step 17: hidden tags preferences and search/list filtering/tests on batch-17-18-content-filters.
- Step 18: per-title Safe/Suggestive/Everything filter and independent adult-source catalog toggle on same branch at da2e12b5b. Wider filtering callsites and hidden count UI still need auditing.
- Step 17-18 cumulative CI: run 38298920743 queued at last observation, not verified.
- Step 19 and onward: not started.
- Only Tunnel to Surface may change GitHub. Request tunnel first at every new work turn.
