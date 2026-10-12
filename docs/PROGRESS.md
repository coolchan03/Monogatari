# Monogatari progress

Updated: 2026-10-12 14:26 CDT (last observed, not a detected timeout)

- Step 1: complete; original Kotatsu archived and Monogatari code set as default branch.
- Step 2: complete; agent context and progress tracking.
- Step 3: code/build verified with release R8 artifact, CI 38289292970. Device smoke test pending.
- Steps 4-5: code/build verified, CI 38292011053, merged to default devel at e497d88de. New-package installation and backup-restore on device pending.
- Step 6: pending signed release/keystore and secrets (not automatically generated).
- Step 7: manual developer verification pending.
- Step 8: configured 15 repo URLs as GitHub Actions secret; BuildConfig defaults, secure confirmation-based repo deep link. Commits 32358643e and ec9c9c384. Latest combined CI pending.
- Step 9: scoped JS extension cookies to allowed source hosts, with host matching tests. Commit e6d1901ab. CI pending.
- Step 10: SHA-256 verification, trust-on-first-use/version-integrity checks and tests. Commit e096ee9a2. CI pending. Extension repository action to populate source hashes not yet implemented.
- Step 11: host-scoped TLS bypass and migration away from global bypass. Commit 91990813e. CI pending; device/network safety test pending.
- Step 12: PBKDF2 app PIN with legacy MD5 upgrade and tests. Commit 446582cfd; compile fixes in commits 91990813e and 68cceeac1. CI pending.
- Step 13: privacy defaults and explicit first-run 18+ choice. Commit 76271d673. CI pending.
- Step 14: diagnostic tools moved to Advanced, silent healthy first-run check. Commit 3531e32cc. CI pending.
- Steps 15-16: search retry/menu improvements and genre tag synonyms with tests. Commit 72849c90a. CI pending.
- Steps 17 onward: not yet implemented.
- Latest cumulative code branch: batch-15-16-search-quality at 68cceeac1.
- Latest cumulative GitHub Actions: 38297511275 (running at last observation).
- Default devel only contains verified Steps 1-5 as of this checkpoint.
- Only Tunnel to Surface may be used for edits, git and GitHub actions. It must be the first action on a new work turn.
