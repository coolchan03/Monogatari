# Mascot: a small cast instead of one character (#80, #81)

Status: **idea only, nothing drawn yet.** You are unsure about a single mascot, so here is a way to avoid choosing one: a small cast,
one creature per kind of media, each shown only in quiet places.

| Section | Creature | Why it fits |
|---|---|---|
| Manga / Comics | Teal fox with a bookmark-shaped tail | The bookmark tail is the hook; foxes are classic "reader" characters |
| Books / Novels | Owl or a small paper crane | Late-night reading; the crane folds from a page |
| Video / Anime | Glow-moth | Drawn to screens and light |
| Audio | Tanuki drumming on its belly | A built-in sound gag |
| News | Magpie | Collects shiny bits of information |

**Rules so it never gets in the way**
- App icon: one simple shape (the bookmark tail or a monogram), not the whole cast.
- The cast appears only in empty states (empty library tab, no results) and first-run tips; never as decoration on working screens.
- One style: flat, two or three colours from the app's accent palette, so it follows the chosen accent colour.

**How it would be built (small):** a `MascotCast` table mapping media type to a vector drawable; empty states pick the drawable for the
section they are in. Each creature is a single vector file, so any of them can be dropped without touching code.

**What I need from you:** which creatures to keep (all five, or a subset), and whether the app icon should be the fox's bookmark tail.
Once you pick, I will draw the vectors and wire the empty states.
