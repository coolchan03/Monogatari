#!/usr/bin/env python3
"""Static regression checks for the settings-style More and book catalog layouts.

This supplements Android unit tests and device accessibility review; it does
not claim to replace a screen-reader or font-scale test on real hardware.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
res = root / "app" / "src" / "main" / "res"
src = root / "app" / "src" / "main" / "kotlin" / "org" / "koitharu" / "kotatsu"
android = "{http://schemas.android.com/apk/res/android}"

def element_by_id(path: Path, name: str):
    tree = ET.parse(path)
    matches = [e for e in tree.iter() if e.get(android + "id") == "@+id/" + name]
    assert len(matches) == 1, f"Missing or duplicate element {name} in {path}"
    return matches[0]

more = res / "layout" / "item_monogatari_settings_destination.xml"
catalog = res / "layout" / "item_monogatari_catalog.xml"
row = ET.parse(more).getroot()
assert row.get(android + "clickable") == "true"
assert any(e.get(android + "minHeight") == "72dp" for e in row.iter()), "More touch target too small"
title = element_by_id(more, "monogatari_book_item_title")
summary = element_by_id(more, "monogatari_book_item_progress")
assert title.get(android + "textAppearance") and summary.get(android + "textAppearance")
assert not title.get(android + "textSize"), "More row must support dynamic font scale"
action = element_by_id(catalog, "monogatari_catalog_item_action")
assert action.get(android + "layout_height") == "48dp"
assert action.get(android + "minHeight") == "48dp"
assert action.get(android + "visibility") == "gone"

more_activity = (src / "main" / "ui" / "MoreActivity.kt").read_text(encoding="utf-8")
book_activity = (src / "core" / "media" / "BookLibraryActivity.kt").read_text(encoding="utf-8")
adapter = (src / "core" / "media" / "CatalogResultsAdapter.kt").read_text(encoding="utf-8")
assert "R.layout.item_monogatari_settings_destination" in more_activity
assert "screenWidthDp >= 720" in more_activity
assert "override fun onSaveInstanceState" in book_activity
assert 'outState.putString("catalog_provider"' in book_activity
assert "resultsAdapter.showLoading()" in book_activity
assert "fun showLoading()" in adapter
assert "holder.action.isVisible" in adapter

for relative in ("values/strings.xml", "layout/activity_monogatari_book_catalog.xml"):
    ET.parse(res / relative)
print("UI static checks passed: More layout, dynamic text, 48dp actions, skeletons, state restoration")
