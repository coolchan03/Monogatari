![Monogatari](docs/monogatari-banner.svg)

# Monogatari (物語)

**Manga · Novels · Anime — A World of Stories in One Place**

Monogatari is an Android reader built on Kotatsu-Redo with Mangayomi-style JavaScript extensions.

## Latest signed beta APK

**[Download Monogatari v0.13.10 signed beta APK](https://github.com/coolchan03/Monogatari/releases/download/v0.13.10-signed-20261015/Monogatari-release.apk)**

[Release notes](https://github.com/coolchan03/Monogatari/releases/tag/v0.13.10-signed-20261015) | [Latest development source (devel)](https://github.com/coolchan03/Monogatari/tree/devel) | [83-feature completion tracker](docs/ROADMAP_83_TRACKER.md) | **[New-chat handoff and next steps](docs/MONOGATARI_NEXT_CHAT_HANDOFF.md)**

This is a **signed beta prerelease**, not the first official v1.0.0. The development branch is newer; the first official release will follow completion of the remaining Android features and CI. Test data should be backed up before switching between APK signing certificates or package variants.

## Features

- Unified search across built-in and custom sources
- Manga, novels and EPUB, and direct-stream anime
- Source presets, pinning, and publication-year filters
- CAPTCHA/Cloudflare support and source preferences

## Status

Active development; some third-party sources and video extractors are unsupported.

The Android finishing work is in [draft PR #13](https://github.com/coolchan03/Monogatari/pull/13) (branch `finish-83-20261015`). The [14-item code ledger](docs/CODE_FINISHING_14.md) distinguishes implemented code from final provider and device verification. A dedicated **code-compilation-only** GitHub Action checks draft PRs without running feature tests or publishing APKs. Official v1.0.0 remains unreleased.

## Source repositories

[Curated extensions](https://github.com/coolchan03/Best-BL-Mangayomi-Extensions)

## Credits

Based on [Kotatsu-Redo](https://github.com/Kotatsu-Redo/Kotatsu-Redo) and inspired by [Mangayomi](https://github.com/kodjodevf/mangayomi). Original contributors retain their credits.

### Unified Books search development

Draft [PR #13](https://github.com/coolchan03/Monogatari/pull/13) now shares the full eleven-provider public Books registry with the global search, displays partial results as providers answer, and provides explicit source-availability notices. Where a publisher supplies a compatible free EPUB, global search can save it into the local reader library. Features remain **in development, not device-verified**.
