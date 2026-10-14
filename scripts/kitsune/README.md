# Kitsune art generator

The mascot drawings are generated from code so every form shares one face and stays consistent.

    python3 scripts/kitsune/build_kitsune.py

Writes `kitsune_*.xml` and `kitsune_*_line.xml` (plus `drawable-night` line variants), the launcher icon vectors, the legacy launcher PNGs,
the Play Store icon and the widget previews. PNG export needs Playwright with Chromium (`npm i -g playwright`; set `PLAYWRIGHT_MODULE`
to its folder if `npm root -g` is not where it lives). To replace a drawing with hand-made artwork instead, overwrite the XML/PNG file of the same name.
