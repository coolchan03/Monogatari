# Source fix tracker (2026-11-26)

Status values: **in jar** = present in the pinned parser artifact (checked by inspecting the artifact, not by running it);
**not started** = no code written; **verified** = a test or live check actually ran (none yet in this session: no Android SDK, so no Gradle run).

## Parser pin
- `gradle/libs.versions.toml`: `parsers` b049587f96 -> 8e4b04f3df (JitPack serves the .jar/.module/.pom; jar sha256 c865d9fa27e713dc76c18665e89c39be5ca1c7bf4a865a55d68252b1a7ae84d8).
- Artifact comparison b049587f96 -> 8e4b04f3df (class list only): 34 entries differ. New: `site/mangareader/en/DrakeScans` (+inner classes), `site/id/Mgkomik` (moved from `site/madara/id/`), `AtsuMoe` helpers (`fetchPagesPastLimit`, `unshuffleIfNumeric`), `GroupleParser$SearchFilters`. No change to the loader/contract classes (`MangaParserSource`, `ParserMangaSource`, factory) by name. The parser source-name set extracted from `MangaParserSource$Companion` is identical (1,123 names) in both revisions, so source IDs and the retired-source fixture are unaffected.
- NOT verified: that `:app` still compiles and the tests pass against it. Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` before relying on the bump; revert the one line in libs.versions.toml if anything breaks.
- Manifest deep links added: drakecomic.net, mangaworld.me (+www), mangaworldadult.org (+www). Old hosts kept for old links.

## Native fixes after b049587f96 (upstream Kotatsu-Redo)
| Source | Upstream commit | Status |
|---|---|---|
| MangaWorld / MangaWorldAdult | c840ea6 | in the parser library; classes changed in place (not visible in a class-list diff). Not run. |
| DrakeScans | bd33635 | in jar (new class). Not run. |
| MgKomik | d29dc97 | in jar (moved class, same source name). Not run. |
| Grouple (ReadManga/SelfManga) | - | `SearchFilters` class added in jar. Not run. |
| AtsuMoe | 1f64f7b | in jar (page-order helpers). Not run. |

## Keiyoushi compatibility ports
These sources are implemented in the external kotatsu-parsers-redo library, not in this repository, so fixing them needs a fork of that library
(documented, reproducible) plus a build environment. **Not started**: Softkomik (76a5f1f, 6429074), MangaDotNet (numeric chapter IDs),
Ikiru (8a7401d), Manganato (5244cf4), MangaLib (8597763, e628b38), HoneyManga (7e5d0a2), ZeistManga (afbab6c), DoujinDesu (28a8db9),
Merlin Scans (48b0840), Catharsis World (3323a32), MadaraDex (71d3f76), AquaManga (48284e3), DuaLeoTruyen (fdb08c3), Rinko Comics (8688d9d),
and the P2 list. Upstream commit hashes are as given in the task, not re-checked against upstream.
