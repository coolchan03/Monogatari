# Approved Kitsune sticker artwork

The old programmatic vector art generator has been retired. The 8 approved compact mascot images are committed as PNG drawables in app/src/main/res/drawable-nodpi. The hidden human form is the eighth image; it is not a normal navigation item.

The compact transparent 4x2 sprite source is in sticker_atlas.b64. To regenerate the individual 256px PNGs using Pillow:

    python scripts/kitsune/export_stickers.py

MascotArtwork.kt remaps teal accents to the current Material primary colour while leaving white, skin and gold intact. See docs/MASCOT_INTEGRATION.md for screen placement and testing. Do not regenerate the deleted old vectors or launcher artwork.
