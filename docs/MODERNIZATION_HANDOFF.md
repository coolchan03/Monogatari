# Modernization handoff (2026-11-10)

## A. Completed engineering (local commits on `ccr-c0c4a160-e5p0d2`, unpushed)
- `a30cf50f0` merge of v1.8.1 (security fixes missing from devel)
- `aca6fd651` parsers `b3c0000245` -> `68f3fed395`
- `8cf2a4052` OkHttp 5.3.2 -> 5.5.1
- `ceec04695` NewPipeExtractor v0.26.5 -> v0.27.0
- `571833268` JS extension publisher pinning (`JsPublisherPolicy`, enforced in `JsSourceStore`)
- `216359184` LogRedactor + GitHub workflow hardening (pinned actions, least privilege)
- publisher-change approval UI (Sources screen)
- Retry-After bug fix (`RetryAfterParser`) and bounded download backoff
- docs commits: plan, regression results, source status, ledger

## B. Tested
`./gradlew :app:testDebugUnitTest --console=plain --max-workers=2` (ANDROID_HOME=/home/user/android-sdk): 919 tests, 0 failures and `assembleDebug` OK after the last change. See REGRESSION_TEST_RESULTS.md. No CI run exists for these commits (nothing pushed).

## C. Remaining technical work
Everything not marked in MODERNIZATION_PLAN.md as IMPLEMENTED/TESTED: Parts 2.2-2.5 (individual site repairs), 3, 4, 5.2-5.4, 6.2-6.3, 7.2, 7.3, 8, and 9 beyond unit tests. Specific gaps in finished items: no update-history record; publisher UI never run on a device; workflow job split and APK provenance not done; pinned action SHAs unverified by a real CI run.

## D. Manual review needed
Playback on NewPipe 0.27.0 (YouTube search/stream/subtitles/live), HTTPS and Cloudflare sources on OkHttp 5.5.1, a library/history sanity check after installing a build over existing data (backup first), and any of the 15 newly added sources you want enabled.

## E. Release readiness
Not ready for a signed release. Lint, release/R8 build, instrumentation and cold-start smoke tests have not been run; nothing is device-verified.

## F. Next steps for another session
1. Set `ANDROID_HOME`, retry Gradle on HTTP 429.
2. Run `assembleDebug`, lint and `assembleRelease` on this branch.
3. Diff parser-library history (`b3c0000245..68f3fed395`) against app-side overrides; add fixture tests for the sites listed in Part 2.2.
4. Split the publish workflow into build and publish jobs; add provenance attestation (Part 7.3).
5. Review Sentry/crash-report config against `LogRedactor` (Part 7.2).
6. Then the reader/download/history work (Parts 3-4).
