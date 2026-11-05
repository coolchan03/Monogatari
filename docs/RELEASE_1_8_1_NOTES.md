# Monogatari 1.8.1 — Source safety and reliability

## Improvements

- **Optional HTTP custom repositories.** Source repositories still default to HTTPS. Adding an HTTP index now requires an explicit security warning and confirmation, followed by the usual source preview and trust confirmation.
- **Repository redirect protection.** The repository-index fetcher rejects cross-protocol redirects rather than silently downgrading a secure request.
- **Book import limits.** Imported books are streamed with a 256 MB cap; failed imports discard their temporary files.
- **Safer backup previews.** Settings-preview data is bounded at 2 MB before parsing.
- **Credential safety.** OPDS catalog URLs cannot include a username/password over HTTP.
- **Maintenance.** Additional URL tests, GitHub CI checks for audit branches and pull requests, and corrected release documentation.

## Known limitations

- HTTP opt-in applies to custom repository indexes, not unencrypted JavaScript source code downloads or APK downloads.
- HTTP is not encrypted or authenticated, and a repository index can be modified by others on the network. Prefer HTTPS.
- New functionality is CI-tested, not yet validated on all real source providers or on both Android devices.
- Larger app performance, accessibility, and provider-integration improvements remain tracked separately.
