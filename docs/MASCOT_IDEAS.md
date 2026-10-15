# Kitsune, the spirit fox (#80, #81)

Decision (owner): the mascot is **Kitsune**, a spirit fox who takes a different form for each kind of media, drawn in an anime style,
with line-art variants, replacing all inherited Kotatsu art, and a hidden muscular Easter egg (a human with fox ears).

## Art (generated from code, `scripts/kitsune/`)

Colour set (hero art) and a matching **line-art set** (`*_line.xml`, one ink colour: dark by day, pale at night):

| Form | Look | Colour file | Line file | Where |
|---|---|---|---|---|
| Default | teal fox, spirit flames, nine tails | `kitsune_default` | `kitsune_default_line` | library "All" tab; line: generic empty lists |
| Manga | teal, speech bubble, bookmark ribbon | `kitsune_manga` | `kitsune_manga_line` | library Manga tab; line: empty favourites |
| Books | amber, round glasses, open book | `kitsune_book` | `kitsune_book_line` | library Book tab; line: empty history |
| Video | violet, glow aura, play flame | `kitsune_video` | `kitsune_video_line` | library Video tab |
| Audio | coral, headphones, music note | `kitsune_audio` | `kitsune_audio_line` | library Audio tab; line: empty local list |
| News | blue, newspaper | `kitsune_news` | `kitsune_news_line` | empty feed (line); colour ready |
| Kitsune-kun | anime human with fox ears and tails, double-biceps pose | `kitsune_buff` | `kitsune_buff_line` | Easter egg |

Style: anime eyes with highlights, kitsune cheek marks and forehead diamond, spiky cheek fluff, fan of nine tails, hitodama flames.

## Replaced Kotatsu art

- Launcher icon (adaptive foreground, themed monochrome), legacy launcher PNGs (Android 6-7), Play Store icon, Android 12+ splash icon.
- Widget preview images (they were a collage of real manga covers).
- Empty-state illustrations for lists (now Kitsune line art).

Regenerate everything with `python3 scripts/kitsune/build_kitsune.py` (see `scripts/kitsune/README.md`).

## Easter egg

In the unified library, when a tab is empty Kitsune appears; tap her quickly 7 times (each tap within 2 seconds of the last) to meet Kitsune-kun.

## Honest limits

This is hand-coded vector art: stylised and consistent, but not the work of an illustrator. To use real artwork, overwrite the XML/PNG file
of the same name; nothing else needs to change. Still unchanged: the in-app storybook illustrations (`illustration_monogatari_stories`,
onboarding hero), which are Monogatari's own, not Kotatsu's.

## Not done yet

- First-run tips and the News screen empty state do not show Kitsune yet.
- More Easter-egg entrances (for example the About screen), if wanted.
