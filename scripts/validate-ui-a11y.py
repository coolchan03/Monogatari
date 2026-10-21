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
# The catalog UI lives in BookCatalogFragment (BookLibraryActivity only hosts it).
book_activity = (src / "core" / "media" / "BookCatalogFragment.kt").read_text(encoding="utf-8")
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
for item_id in ("monogatari_catalog_item_title", "monogatari_catalog_item_summary"):
    text = element_by_id(catalog, item_id)
    assert text.get(android + "maxLines") is None, f"{item_id}: truncated at 200% font size"
    assert text.get(android + "ellipsize") is None, f"{item_id}: ellipsized at 200% font size"
    assert text.get(android + "textAppearance"), f"{item_id}: missing scalable typography"
for name in ("layout-w600dp", "layout-w840dp"):
    preview = res / name / "activity_search.xml"
    element_by_id(preview, "search_preview_scroll")
    element_by_id(preview, "search_preview_pane")
    for action_id in ("search_preview_open", "search_preview_read"):
        button = element_by_id(preview, action_id)
        assert button.get(android + "layout_height") in ("wrap_content", "48dp"), "fixed-size preview action"
assert "SearchMetadataFilterDialog" in (src / "search" / "ui" / "multi" / "SearchMenuProvider.kt").read_text(encoding="utf-8")
assert "contentDescription" in (src / "search" / "ui" / "multi" / "SearchMetadataFilterDialog.kt").read_text(encoding="utf-8")
qa = res / "layout" / "activity_monogatari_test_center.xml"
qa_toolbar = element_by_id(qa, "qa_toolbar")
assert qa_toolbar.get(android + "layout_height") == "wrap_content", "QA toolbar clips text on large font settings"
assert qa_toolbar.get(android + "minHeight") == "?attr/actionBarSize"
for action_id in ("qa_pass", "qa_fail", "qa_untested"):
    button = element_by_id(qa, action_id)
    assert button.get(android + "minHeight") == "48dp", f"{action_id}: insufficient touch target"
    assert button.get(android + "maxLines") == "2", f"{action_id}: cannot wrap with larger font"
qa_activity = (src / "core" / "qa" / "MonogatariTestCenterActivity.kt").read_text(encoding="utf-8")
assert "setSupportActionBar(ui.qaToolbar)" in qa_activity
assert "setNavigationContentDescription(R.string.monogatari_navigation_back)" in qa_activity
print("UI static checks passed: scalable text, accessible 48dp actions, adaptive search panes and consistent QA chrome")
