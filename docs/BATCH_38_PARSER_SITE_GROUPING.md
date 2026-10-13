# Step 38a: Parser / JS source host grouping

Run: 2026-10-12 overnight, initial batch. Branch: `batch-38-parser-site-hosts`, based on verified `devel` commit `1f91e9021`.

## Implemented in this batch
- Resolve Kotatsu parser's active configured domain through its `ParserMangaRepository` and include that domain in Explore and global-search host grouping; previously parser sources could never participate in website grouping.
- Preserve fallback order and media type separation; when a parser domain is unavailable/throws, it remains visible as an independent source instead of silently disappearing.
- Canonicalize bare hostnames and mobile/www prefixes consistently for source setup, website groups, and Dart compatibility checks; reject non-HTTP(S) schemes.
- Add targeted unit regressions for parser grouping, unknown domains, repeated prefixes, and non-HTTP schemes.

## Acceptance / remaining work
- Source changes are not CI-accepted until the exact commit's GitHub Actions Build check succeeds; no device test has been performed.
- Step 38b automatic dead-library title move with read position/categories/stats preservation and Undo is **NOT IMPLEMENTED**. Must be tested against real backups before attempting any destructive migration.
- Other earlier gaps: Step 37 post-repair automatic site re-test, Stage H integration branches and Step 40 external repository workflow must not be labelled complete based on this batch.
- Protect the existing untracked `.release-temp/` and `source-audit/` directories in the main worktree.
