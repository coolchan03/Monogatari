# Monogatari live work checkpoint

**Last observed local time:** 2026-10-12 14:26 CDT. This is NOT the instant of a model timeout; an unexpected timeout cannot be detected directly.

**Latest verified operation:** Via Tunnel to Surface, queried GitHub Actions and local status. CI run 38297511275 was in progress for cumulative branch batch-15-16-search-quality, commit 68cceeac1. Branch working tree had only this checkpoint file modified plus pre-existing untracked .release-temp/ and source-audit/ (must be preserved).

**Default:** devel = e497d88de (Steps 1-5, verified); devel CI run 38295603277 passed.

**Stage C-D cumulative commits:**
- 32358643e: 15 repo defaults moved to MONOGATARI_REPOS Actions secret and BuildConfig.
- e6d1901ab: ScopedCookieJar and tests.
- ec9c9c384: confirmed monogatari://setup?repos= deep link.
- e096ee9a2: JS source file SHA-256/version checks and tests.
- 446582cfd: PBKDF2 PIN/MD5 migration.
- 76271d673: privacy defaults/18+ welcome selection.
- 91990813e: exact-host TLS exception restrictions and nullable PIN compiler fix.
- 3531e32cc: diagnostics under Advanced.
- 72849c90a: search concurrency/retry state and BL/GL synonyms.
- 68cceeac1: escaped Kotlin PIN test fixture delimiter.

**Failure history:** CI 38296362859 and 38296556311 failed due nullable appPassword compile error; repaired in 91990813e. CI 38296936767 failed due literal `"pbkdf2$bad"` unescaped in test source; repaired in 68cceeac1. Older branches inherit these errors and do not need individual repair; cumulative tip does.

**Current task / next exact operation:** Request Tunnel to Surface access FIRST at the start of next user turn. Check the latest cumulative CI run 38297511275, including unit tests, debug APK and unsigned R8 APK artifacts. If failed, retrieve logs via gh, repair the latest cumulative branch, and rerun. If succeeded, fast-forward latest cumulative tip to devel, verify git ls-remote, update progress and inspect default-branch CI. Do NOT merge before green.

**Outstanding gates:** Step 6 signed release requires keystore and secrets; Step 7 external verification; Android device app-backup/restore, extension browsing, cookies, TLS tests; Step 10 extension-repository index hash generation action; Steps 17+.

**Rules:** Only Tunnel to Surface may edit/push/release GitHub content. Use target 20-30-minute batches and record exact operation, branch, commit, CI run, last observed time. Preserve local user folders .release-temp/ and source-audit/. Do not claim successful CI, device tests or signed releases without evidence.
