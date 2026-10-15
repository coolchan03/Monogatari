# Large-screen Android layouts (2026-10-14)

## Large-screen audit: what was wrong and what changed

| Problem found | Fix |
|---|---|
| All tablet resources were `-w600dp-land`, so portrait tablets, unfolded foldables and tall ChromeOS windows got the phone UI (no navigation rail, no two-pane settings, no side sheets). | Moved to `-w600dp` (`layout-w600dp/`, `values-w600dp/`). Details keeps `layout-w600dp-land` (side-by-side needs landscape). |
| CI "tablet" smoke run used 711x1067dp, so no tablet resource was ever exercised. | `scripts/ci-smoke-startup.sh` now cold-starts on 800x1280dp (portrait tablet) and 1280x800dp (landscape tablet/laptop) and saves screenshots. |
| 12 Monogatari screens (More, Books, library, feeds, local readers, import, WebDAV, licences, Discover) had no toolbar, no Up button and ignored system-bar/cutout/ChromeOS caption insets. | `core/ui/MonogatariChrome.kt`: toolbar + Up + menus, insets, centred max-width column (760dp text, 1200dp lists). |
| Card lists capped at 2 columns. | `MonogatariChrome.columns()` - one column per ~340dp, up to 4. |
| Tablet details layout showed an old RatingBar no code was connected to; community rating was unusable on tablets. | Replaced with the phone layout's working score button + stars. |
| Android 16+ ignores orientation requests when smallest width >= 600dp, so rotate/lock buttons did nothing on tablets. | Hidden there (`ScreenOrientationHelper.isOrientationControllable`). |
| No keyboard support in PDF/EPUB/text readers; no search shortcut. | Arrows, Page Up/Down, Space, Home/End, `[ ]` chapters, Ctrl +/- text size; Ctrl+F or `/` opens search. |
| No drag-and-drop. | Drop EPUB/PDF/text files onto Books to import. |

Already good: no orientation locks or `uses-feature` blockers in the manifest (installs on ChromeOS/tablets), adaptive manga grids,
navigation rail, mouse-wheel and right-click support in lists and the reader, foldable-aware double-page mode, PiP video.

Still open: desktop-width navigation drawer (#62), true two-pane library/search/details (#63), foldable posture testing (#66).
