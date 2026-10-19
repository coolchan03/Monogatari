"""Export the app's Kitsune mascot images from the full-resolution originals in art/kitsune/originals.

Each role is trimmed to its visible artwork, centred on a transparent square and scaled to 512px, so it stays sharp
when shown large on high-density phones and tablets. Run from the repository root:

    python3 scripts/kitsune/export_stickers.py
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ORIGINALS = ROOT / "art/kitsune/originals"
OUT = ROOT / "app/src/main/res/drawable-nodpi"
SIZE = 512

# App role -> original artwork (see art/kitsune/README.md).
ROLES = {
    "welcome": "cheerful_teal_tailed_kitsune.png",
    "reading": "chibi_kitsune_reading_a_book.png",
    "search": "curious_kitsune_with_magnifying_glass.png",
    "celebrate": "joyful_teal_eyed_kitsune_sticker.png",
    "rest": "sleeping_teal_tailed_kitsune_mascot.png",
    "sad": "sad_teal_fox_spirit_sticker.png",
    "collection": "winking_fox_with_treasured_books.png",
    "news": "chibi_fox_spirit_reporter_sticker.png",
    "audio": "whimsical_kitsune_headphone_sticker.png",
    "human": "flirty_teal_accented_kitsune_greeting.png",
}


def export(name: str, source: str) -> Path:
    image = Image.open(ORIGINALS / source).convert("RGBA")
    box = image.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox() or (0, 0, *image.size)
    art = image.crop(box)
    side = int(max(art.size) * 1.04)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(art, ((side - art.width) // 2, (side - art.height) // 2), art)
    canvas = canvas.resize((SIZE, SIZE), Image.Resampling.LANCZOS)
    target = OUT / f"kitsune_sticker_{name}.webp"
    canvas.save(target, "WEBP", quality=90, method=6)
    old = OUT / f"kitsune_sticker_{name}.png"
    if old.exists():
        old.unlink()
    return target


if __name__ == "__main__":
    for role, file in ROLES.items():
        path = export(role, file)
        print(f"{path.relative_to(ROOT)}: {path.stat().st_size // 1024} KB")
