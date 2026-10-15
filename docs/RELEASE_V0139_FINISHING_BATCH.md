# Monogatari 0.13.9 — More and Books UI finishing batch

Built from the latest `devel` branch (including the new signed-release workflow).

## Changes made

- **Settings-style More:** Replaced the bookshelf-like destination tiles with compact
  Material rows matching Settings visual density; one column on phones and two on
  wider tablet/foldable windows. Category headings and all existing destinations
  are retained. Kitsune now has an accessible description.
- **Books Catalog:** Added a dedicated, accessible 48dp `Open EPUB` or `View listing`
  button on applicable book results. Existing card tap still allows choosing
  alternate providers. EPUB links open through the user's browser/app chooser,
  with no DRM bypass.
- **Loading and restoration:** Shows four static placeholder cards during catalog
  search rather than a suddenly blank screen; clears older provider errors before
  each search. Search query is remembered, and the selected provider/query are
  reloaded after Android activity recreation or rotation.
- **Reliability:** Split the signed-release CI workflow into test and release steps
  and reset Gradle compiler daemons between them, with a bounded release worker
  count. This addresses the earlier Kotlin release compiler heap OOM; the fix
  still needs a successful signed-release dry run.
- **Automated UI regression:** Added a Python check to both GitHub workflows for
  accessible target sizes, responsiveness, catalog actions, static XML validity,
  loading skeletons, and state preservation. Added guided device checks.

## Tracker status

57 DONE, 26 PARTIAL, 0 PENDING across the 83 Android-only roadmap items.
The More-screen layout (#12) is implemented in code. Shared styling for every
specialized screen, remaining video providers, in-player subtitles, OCR bubble
segmentation, advanced cross-source merging, large-screen tests, and physical
provider/device checks are not fully finished.

## Required validation

1. `python3 scripts/validate-ui-a11y.py` passes.
2. GitHub unit tests, debug APK assembly, optimized release and Android cold start pass.
3. Review More in portrait/landscape at system font size 200%; no clipped labels.
4. In Books Catalog search a known title, test the `Open EPUB`/`View listing`
   controls, use Back, rotate while searching, and confirm provider/error recovery.
5. The previously failing signed-release workflow must pass a **dry run** with
   valid existing GitHub secrets before producing a production-signed APK.
   Do not store signing secrets in the repository or a local build.
6. Actual nearby transfers and optional third-party source credentials still
   require physical-device or authorized-account testing.
