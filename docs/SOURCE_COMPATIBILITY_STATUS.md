# Source compatibility status

No source was live-tested in this pass. The only evidence is structural.

- Parser library moved to `68f3fed395`. 0 `MangaParserSource` ids removed, 15 added. Package moves seen in the jar diff (e.g. `en/MangaK` -> `mangak/en/MangaK`, `madara/en/TopManhua` -> `en/TopManhua`, `madara/id/LumosKomik` -> `id/LumosKomik`, `mangareader/en/Elftoon` -> `en/Elftoon`) did not change enum ids.
- `RaijinScans` (`madara/fr`) class file is gone from the jar, but its enum id remains; whether it now resolves to another implementation or is marked broken has not been checked.
- Sites named in the assignment (ManhuaTop, MG Komik, ManhwaIndo, RocksManga, MilfToon, Softkomik, SkyMangas, Temple Scan, Catharsis World, AComics, WestManga, LikeManga, Kagane, Mangago, Atsu.moe, Comix, ...): status UNKNOWN. Their fixes belong to the parser library; next step is to diff the library's history for each and check the app for local overrides.
