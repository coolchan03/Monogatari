# Monogatari v0.13.6: Entertainment-first pending-feature batch

## Changed scope
Per user feedback, Monogatari is an **entertainment reader/watcher/listener**, not a textbook or education app. Removed the uncommitted OpenStax integration; no TED educational videos are included. Requirements #43, #44 and #56 were rewritten as fiction, fanfiction and entertainment-video goals while preserving all 88 numbered slots.

## Implemented / available for testing
- #38 (Partial): Standard Ebooks **public new-releases Atom feed**, surfaced as an optional Books Catalog provider; links open legitimate title pages. The complete OPDS collection requires membership and is **not** claimed to work unauthenticated.
- #60 (Partial): Add and manage user-supplied HTTPS RSS/Atom subscriptions for fiction podcasts, audio drama, and entertainment video episodes. Browse covers/titles, filter Audio/Video, open direct public enclosure with an installed media player, or open the official show page. No automatic polling or academic default feeds.
- Existing unfinished v0.13.5 work: public books/video/audio Discover cards, correct source entry routes, additional unit tests and retry-safe emulator startup verification.
- Guided Test Center now explicitly tests the public fiction provider and entertainment subscriptions.

## Testing gates and limitations
- Android JVM unit tests cover RSS/Atom parsing, URL filtering, DTD/XXE blocking, media type classification, legal public links and the earlier cross-catalog sorting behavior.
- GitHub Actions compiles unit/debug/R8 and cold-starts Android emulators at phone and simulated tablet widths. Actual Motorola/tablet and individual RSS playback remain device tests.
- Subscription URLs stay on the device in private app preferences; this feature does not ask for account passwords, credentials, or background subscriptions.
- URL redirects/rate limits, subtitle/chapter support, authorized premium streams, local drag-drop, true Windows support and a final original mascot remain unfinished requirements.

See [all 88 roadmap statuses](ROADMAP_88_TRACKER.md).
