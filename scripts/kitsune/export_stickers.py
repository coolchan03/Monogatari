"""Rebuild the compact, transparent Kitsune stickers from their source atlas."""
from pathlib import Path
from io import BytesIO
from PIL import Image
import base64

ROOT = Path(__file__).resolve().parents[2]
source = Path(__file__).with_name("sticker_atlas.b64")
image = Image.open(BytesIO(base64.b64decode("".join(source.read_text().split())))).convert("RGBA")
assert image.size == (256, 128), "Expected a 4x2 atlas of 64px square stickers"
out = ROOT / "app/src/main/res/drawable-nodpi"
forms = ("welcome", "reading", "search", "celebrate", "rest", "sad", "collection", "human")
for index, form in enumerate(forms):
    tile = image.crop((index % 4 * 64, index // 4 * 64, (index % 4 + 1) * 64, (index // 4 + 1) * 64))
    tile = tile.resize((256, 256), Image.Resampling.LANCZOS)
    tile.quantize(colors=32, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(
        out / f"kitsune_sticker_{form}.png", optimize=True
    )
print("Exported", len(forms), "Kitsune sticker drawables")
