# Kitsune mascot artwork

The owner-supplied full-resolution originals (1254x1254 PNG) live in `art/kitsune/originals/`; see
`art/kitsune/README.md` for which picture plays which role. The app ships 512px WebP exports in
`app/src/main/res/drawable-nodpi/kitsune_sticker_*.webp`, made with:

    python3 scripts/kitsune/export_stickers.py

To change a role, edit `ROLES` in the script and re-run it. `MascotArtwork.kt` still tints the teal accents to the
current theme colour at runtime. The earlier 64px sticker atlas was retired on 2026-10-19 (it was the cause of the
blurry mascot); it remains in git history.
