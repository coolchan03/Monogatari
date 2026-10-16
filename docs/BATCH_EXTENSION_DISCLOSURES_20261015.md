# Monogatari 0.13.11 — extension source transparency

This Android-only completion batch addresses roadmap #34.

## Implemented

- Source repository list shows local installed/update availability and publisher-reported health status including new, updating, working, unverified and stale failure.
- Third-party JavaScript sources show a **details and permissions review before installation**. Canceling the review cannot install the source.
- Review contains declared language, author, version, license, code and repository hostnames, optional permissions list and publisher check date.
- No publisher-provided permissions are labelled as granted Android permissions. Missing claims are shown as 'not supplied', not 'none'.
- Source metadata persists with local JS source installations, surviving app restarts and extension updates.
- Unit tests cover Mangayomi index metadata extraction, disclosure summaries, explicit missing permissions and on-disk persistence. The guided on-device test describes the actual UI steps.

## Boundaries

External health reports are **publisher assertions, not live compatibility guarantees**. The app still hides index entries with recent confirmed-broken status; stale failures remain discoverable for manual retest. Source code remains third-party code and the review does not certify it as safe.

Android CI must pass before merging; physical-device testing of installation and screen-reader rendering remains deferred to the first official-version trial.

**Tracker after this batch:** 68 DONE, 15 PARTIAL, 0 PENDING of 83 active Android features.
