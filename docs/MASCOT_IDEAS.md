# Kitsune, the spirit fox (#80, #81)

Decision (owner): the mascot is **Kitsune**, a spirit fox who takes a different form for each kind of media, drawn in an anime style,
with a hidden muscular form as an Easter egg.

## What exists

Hand-drawn vector art in `app/src/main/res/drawable/` (one file per form, so any of them can be replaced with better artwork without touching code):

| File | Form | Shown for |
|---|---|---|
| `kitsune_default.xml` | Teal fox with a spirit flame | Unified library "All" tab |
| `kitsune_manga.xml` | Teal fox, bookmark ribbon, speech bubble | Manga tab |
| `kitsune_book.xml` | Amber fox, round glasses, open book | Book tab |
| `kitsune_video.xml` | Violet fox, glow aura, play button | Video tab |
| `kitsune_audio.xml` | Coral fox, headphones, music note | Audio tab |
| `kitsune_news.xml` | Blue fox, newspaper | (ready; not shown anywhere yet) |
| `kitsune_buff.xml` | Kitsune-kun, the muscular form | Easter egg |

Code: `core/ui/Mascot.kt` (`MascotForm`, `Mascot.formFor`, `TapCounter`). Kitsune appears in the **empty state** of the unified library, in the
form of the selected tab. **Easter egg:** tap her quickly 7 times (each tap within 2 seconds of the last) to meet Kitsune-kun.

## Honest limits

This is stylised flat vector art made by hand-writing shapes: big anime eyes with highlights, cheek marks, blush, pointed ears. It is not
professional illustration. If you commission or draw real artwork, replace the files above (keep the names) and everything else keeps working.

## Not done yet

- App icon still the old one (a Kitsune bookmark-tail icon would be the next step; launcher icons need several sizes and a build check).
- First-run tips and the News screen empty state do not show Kitsune yet (`kitsune_news.xml` is ready).
- More Easter-egg entrances, if you want them (for example the About screen).
