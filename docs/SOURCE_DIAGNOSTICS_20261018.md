# Source and extension diagnostics: recorded results and triage (2026-10-18)

## Previously saved full-sweep result (owner device, before this batch)

| Result | Count |
|---|---:|
| Checks | 1,370 |
| PASS | 356 |
| PARTIAL | 274 |
| FAIL | 740 |

Recorded FAIL categories: DNS 230, OTHER 182, CLOUDFLARE 110, TIMEOUT 100, HTTP_404 72, TLS 21, HTTP_403 20
(the remaining few were spread over smaller categories).

What the numbers mean, read with the classifier that produced them:

- **DNS 230, TIMEOUT 100**: the website could not be reached at all, usually a dead or moved domain,
  sometimes an ISP/regional block. Not a Monogatari code defect. Some can be recovered with DNS over
  HTTPS or an upstream mirror update; many domains are permanently gone.
- **CLOUDFLARE 110, HTTP_403 20**: the website answered but needs a human browser check or refuses
  automated access. Monogatari opens the website's own verification page; it does not bypass these
  protections, so these are not "fixable" in code.
- **HTTP_404 72**: the site is online but the listing/API path changed. These need a parser or extension
  update upstream (Kotatsu-Redo parsers, Mangayomi extension repositories).
- **TLS 21**: handshake/certificate problems on the site side or network interception. Certificate
  checks stay enabled.
- **OTHER 182**: the old classifier could not tell. Part of the cause was in Monogatari's code (fixed
  below): jsoup's `HttpStatusException.message` does not contain the status code (HTTP/2 responses
  carry no reason phrase at all), wrapped network exceptions hid their root cause, and a Mangayomi
  extension that received a 404/DNS failure was reported only by its later `TypeError`.

**Do not read 740 FAIL as 740 broken parsers.** Most failures depend on the website or the network.

## Changes in this batch (code, not yet device-verified)

- `SourceFailureCategory.kt`
  - Network/HTTP evidence is evaluated before script errors.
  - New categories: `CONNECTION` (refused/reset/unreachable), `SERVER_ERROR` (5xx incl. Cloudflare
    52x origin errors), `HTTP_OTHER`, `PARSER` (site answered, parser could not read it),
    `JS_RUNTIME_GAP` (extension needs a Mangayomi host function Monogatari lacks); 410 counts as
    `HTTP_404`, 429 as `RATE_LIMIT`, 401 as `AUTH`.
  - `describeSourceFailure()` keeps the HTTP status and up to three causes.
  - `SourceFailureCause` groups categories by who has to act: website unreachable, access restricted,
    page/API moved, parser/extension defect, secure-connection problem, unclassified.
  - `recategorizeSavedLine()` re-reads saved `OTHER` lines, so an existing saved sweep improves
    without re-running 1,370 network checks.
- `JsHttpBridge` records the last failed request's status/network exception class (no URL, header or
  body) and `JsExtension.lastNetworkProblem` exposes it; JS diagnostics add it as `cause=` evidence.
- `SourceReport.kt`: the copyable report adds a **Likely causes** section with an explanation and next
  step per group, and states how many failures actually point at fixable code. The Test Center summary
  now includes that section.
- Global search: each failed source section has its own **Retry** button; when the failure is a
  CAPTCHA/sign-in/TLS/not-found case the button offers the matching action (Solve, Sign in, Fix, Open
  in browser) through the existing `ExceptionResolver`, then re-queries only that source.
- Unit tests: `SourceFailureCategoryTest` covers the new categories, cause precedence, status
  preservation, saved-line re-categorization and report grouping.

## Known limits

- `lastNetworkProblem` is per extension instance; if the user browses the same source during a sweep,
  the evidence may come from that request. It only affects the category label.
- A category is a triage hint from the error text, not proof. A real re-run of the sweep on the owner
  device is required to produce new counts; the 1,370-check numbers above are the last recorded run.
- Hiding: global search hides only sections with no matches for that query (Hide empty toggle) and skips
  sources with repeated real failures and no recent success (`SourceHealthRecorder`). Cloudflare and
  offline failures are not counted against a source. A diagnostics sweep never disables a source.
