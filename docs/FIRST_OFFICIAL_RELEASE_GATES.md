# Monogatari — first official Android release gates

**Purpose:** Finish all agreed Android functionality before the user's first
two-device installation and improvement report. Native Windows and PWA are
outside scope.

## Progress (code implementation, not physical QA)

83 Android milestones; **67 DONE, 16 PARTIAL, 0 PENDING** on the
post-0.13.10 finishing branch. Compared with the original 27 partials, four
have reached code-complete status in the merged codebase:
- #12: compact More / Settings navigation
- #60: RSS podcast and video playback with chapter seeking/subtitles
- #38: Standard Ebooks searchable public listings + verified compatible EPUB links
- #55: Twitch live/channel, VOD and official clip discovery

Those four milestones were merged after the full GitHub build passed.
Seven additional providers and quality gates are code-complete based on live catalog
checks and fixture tests: #40 Faded Page, #41 Project Gutenberg Australia,
#42 Global Grey and #47 Baen Free Library, plus #50 Vimeo public category/channel/showcase browsing, #53 Bilibili official catalog and #80 robust CI smoke verification. Physical-device review follows.

## Remaining functionality

| Theme | Incomplete numbers | Primary development need |
|---|---|---|
| Cohesive visual design | 11, 14, 17, 18 | Shared readers and search navigation, catalog metadata/borrowing, search restoration, accessibility |
| Manga translation | 21, 23 | Bubble-aware segmentation and vertical/CJK typesetting |
| Search/extension | 29, 32, 34, 37 | Authoritative year, advanced media filters, extension health, preferred source and duplicates |
| Literature providers | 45, 48 | Validate real catalogs, StoryWeaver browsing and legitimate browser-only sites |
| Video providers | 58 | Vimeo channels, Bilibili and Plex final behavior |
| Large screens/accessibility | 63, 66, 69 | Two-pane layouts, foldables/window resizing, accessibility review |
| Automated QA | none | GitHub-hosted unit tests, build, tablet/phone startup and selective retry for runner provisioning are implemented |

Source restrictions must be respected: no CAPTCHA circumvention, no login/DRM
bypass, and no claims that unavailable provider APIs work.

## Definition of the first official build

1. Finish each required code feature or document clearly why its provider
   prevents a supported implementation and what safe fallback is delivered.
2. All application unit tests, compiler/R8 builds, and signed-release checks
   succeed on the exact release commit in GitHub Actions.
3. No failed Android startup or high-severity data-loss issue remains.
4. Update the 83-item tracker with evidence per milestone; keep provider and
   physical-device QA distinct from code completion.
5. Produce one stable, signed v1.0.0 Android APK with release notes, migration
   notes, source disclosures and SHA-256 checksum.
6. Then the user installs it on two Android devices and supplies the guided
   report for a post-launch patch release. Nearby sync is expressly a
   two-device acceptance test, not already device-verified.

No Android SDK or emulator should be installed on the user's laptop,
phone or tablet; CI may use temporary GitHub-hosted Android runners.
