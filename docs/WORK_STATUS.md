# Monogatari live work checkpoint

Last observed: 2026-10-12 14:37:59 CDT. This is NOT a detected timeout.
Tunnel first on every new user work turn. GitHub changes only through Tunnel to Surface.

Current tested default: devel e497d88de (Steps 1-5 code verified), no new merge yet.
Cumulative Steps 8-16: branch batch-15-16-search-quality, tip 68cceeac1. CI run 38297511275 IN PROGRESS with debug-apk uploaded; release R8 still unverified at last observation.
This turn created Steps 17-18 branch batch-17-18-content-filters (from tip 68cceeac1):
- eb94d1fac hidden tags setting, synonym filtering in lists/search, title-level Safe/Suggestive/Everything, settings UI, content-level tests
- da2e12b5b source catalog adult-only toggle separated from title level via MangaSourcesRepository
- CI 38298920743 pending at last observation; 38298768930 superseded/cancel-prone.
Static git diff --check passed; XML resource files parsed locally; full CI remains pending.
Local repo C:\Users\coolc\Codex Projects\Manga-Reader, working branch batch-17-18-content-filters.
Pre-existing untracked .release-temp/ and source-audit/ must stay untouched.

NEXT EXACT OPERATION: Ask for Tunnel to Surface FIRST. Check gh run view 38297511275 and gh run view 38298920743. If 38297511275 passes, default devel may fast-forward to verified 68cceeac1; if 38298920743 passes, it includes prior commits and can fast-forward to da2e12b5b instead. Do not merge failed builds. Diagnose/fix actual logs for failures. Stage F Step19 after tests.
Caveats: Steps 17-18 implementation focused on MangaListViewModel, SearchV2Helper, MangaSourcesRepository; other NSFW filtering callsites and hidden count UI may need follow-up. Step 10 companion repo hash-generation action remains outstanding. Step 6 signing and device backup/restore unverified.
User preference: group tasks ~20-30min, report precise operation and final CI status, no falsely claiming exact timeout detection.
