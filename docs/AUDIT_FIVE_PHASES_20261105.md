# Monogatari five-phase audit tracker — 2026-11-05

Baseline: published v1.8.0 (7738836). Audit branch: audit-fixes-20261104.
CODED means edited; CI PASS means GitHub checked; DEVICE PASS means manually verified.

## Phase 1 — Security and correctness
- CODED: Explicit HTTP custom-repository warning and confirmation; HTTPS stays default.
- CODED: Reject cross-scheme index redirects.
- CODED: Bound imported book copies at 256 MB and delete failed temporary copies.
- CODED: Bound backup settings preview at 2 MB.
- CODED: Reject OPDS credentials in plaintext HTTP.
- CODED: Repository URL regression tests.
- PENDING: Redirect tests, malformed index fuzzing, import/restore concurrency tests and real server authentication.

## Phase 2 — Speed and code quality
- PENDING: Profile startup, global search, Discover, images, reader page prefetch and caches.
- PENDING: Split oversized NearbyOfflineSyncActivity and UnifiedLibraryFragment with behavior-preserving tests.
- PENDING: Measure memory, battery, and network usage before and after optimization.

## Phase 3 — UI/reader accessibility
- PENDING: Foldable/tablet UI tests, dynamic font and card sizes, reader presets and accessibility checks.

## Phase 4 — Entertainment features
- PENDING: Komga/Kavita/Emby, search source picker, offline subtitles, universal Continue shelf, smart prefetch and source repair.

## Phase 5 — Automated verification and release
- CODED: PR and audit branch build-check triggers.
- PENDING: Android lint, security analysis, instrumentation and performance baseline.
- PENDING: Pass CI, merge, build and verify a signed maintenance APK, and publish release notes.

Do not mark all five phases complete from this scoped security patch. Real-device checks remain pending.
