#!/usr/bin/env python3
"""Remove abandoned native-Windows milestones from active Android roadmaps."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
docs = root / "docs"
tracker = docs / "ROADMAP_88_TRACKER.md"
roadmap = docs / "FINISHING_ROADMAP_20261013.md"
source = tracker.read_text(encoding="utf-8")
prior = roadmap.read_text(encoding="utf-8")
assert sum(bool(re.match(r"^\|\s*\d+\s*\|", line)) for line in source.splitlines()) == 88
assert all(re.search(r"^\|\s*" + str(n) + r"\s*\|\s*DROPPED", source, re.M)
           for n in range(71, 76))
assert all(re.search(r"^" + str(n) + r"\.", prior, re.M) for n in range(71, 76))

def change_ref(match):
    number = int(match.group(1))
    return "#" + str(number - 5 if 76 <= number <= 88 else number)

revised = []
for line in source.splitlines():
    m = re.match(r"^(\|\s*)(\d{1,2})(\s*\|)", line)
    if m:
        n = int(m.group(2))
        if 71 <= n <= 75:
            continue
        if n > 75:
            line = m.group(1) + str(n - 5) + line[m.end(2):]
    revised.append(line)
source = "\n".join(revised) + "\n"
source = re.sub(r"#(7[6-9]|8[0-8])\b", change_ref, source)
source = source.replace("88-item", "83-item").replace("Full 88", "Full 83")
source = source.replace(
    "Legend: DONE = implemented in code; PARTIAL = incomplete or awaiting real-provider validation; PENDING = not implemented; DROPPED = explicitly removed from scope. Device QA is separate.",
    "Legend: DONE = implemented in code; PARTIAL = incomplete or awaiting real-provider validation; PENDING = not started. Device QA is separate."
)
start = source.index("**Implementation snapshot after")
end = source.index("\n\n", start)
source = source[:start] + (
    "**Android-only implementation status (2026-10-15):** This list contains 83 active "
    "milestones. The abandoned Windows desktop/PWA project was removed rather than "
    "carried as five dropped entries. New public book catalogs and official-site "
    "discovery are being integrated; live provider/device tests are tracked separately."
) + source[end:]
source = source.replace(
    "| 70 | DONE | Short-term: Android emulator for compatibility/testing only; do not mistake it for native Windows support. |",
    "| 70 | DONE | Android cold-start compatibility is checked on temporary GitHub-hosted runners; no emulator or SDK is installed on user devices. |"
)
# New sources have actual code paths, pending CI and device checks.
for number, description in (
    (40, "Faded Page — official search endpoint, title/author/year/cover metadata and official reading links; device verification pending."),
    (41, "Project Gutenberg Australia — live official CSV catalog, title/author lookup, HTML/text links and regional copyright warning; device verification pending."),
    (42, "Global Grey — searchable official sitemap with safe book/online-text links; device verification pending."),
    (45, "StoryWeaver — official-site search from Books, but native metadata/reader integration still pending."),
    (47, "Baen Free Library — actual free-library catalog cards with covers and official book links; device verification pending."),
    (48, "FictionPress and FanFiction.net — official-site search available, but the sites require browser verification; native metadata parsing is pending."),
    (53, "Bilibili — official video search link available, but native video-source integration/playback is pending."),
):
    pattern = re.compile(r"^\|\s*" + str(number) + r"\s*\|\s*PENDING\s*\|.*$", re.M)
    source, count = pattern.subn("| " + str(number) + " | PARTIAL | " + description + " |", source)
    assert count == 1, ("Missing source status", number)
# Keep the original 83 row identifiers stable below deleted 71-75, except for renumbering.
rows = re.findall(r"^\|\s*(\d{1,2})\s*\|\s*(DONE|PARTIAL|PENDING)\s*\|", source, re.M)
assert [int(number) for number, _ in rows] == list(range(1, 84))
counts = {status: sum(s == status for _, s in rows) for status in ("DONE", "PARTIAL", "PENDING")}
source = source.replace(
    "New public book catalogs and official-site discovery are being integrated;",
    "Status: " + ", ".join(str(counts[s]) + " " + s for s in counts) +
    ". New public book catalogs and official-site discovery are being integrated;"
)
(tracker.parent / "ROADMAP_83_TRACKER.md").write_text(source, encoding="utf-8")
tracker.unlink()

lines = []
inside_windows = False
for line in prior.splitlines():
    if line.startswith("## Priority 5 — Windows"):
        lines.append("## Priority 5 — Android compatibility")
        lines.append("70. Check startup on temporary GitHub-hosted Android test devices; no local emulator installation.")
        inside_windows = True
        continue
    if inside_windows:
        if line.startswith("## Priority 6"):
            inside_windows = False
            line = line.replace("Priority 6", "Priority 6")
        else:
            continue
    if re.match(r"^(7[6-9]|8[0-8])\.", line):
        n = int(line.split(".", 1)[0])
        line = str(n - 5) + line[len(str(n)):]
    lines.append(line)
prior = "\n".join(lines) + "\n"
prior = prior.replace("88-item", "83-item").replace("88 requirements", "83 requirements")
roadmap.write_text(prior, encoding="utf-8")

# Other docs retain historical audit information but link to the current tracker.
for p in docs.glob("*.md"):
    if p.name in ("ROADMAP_83_TRACKER.md", roadmap.name):
        continue
    if p.name == "WINDOWS_PLAN.md":
        p.unlink()
        continue
    original = p.read_text(encoding="utf-8")
    updated = original.replace("ROADMAP_88_TRACKER.md", "ROADMAP_83_TRACKER.md")
    updated = updated.replace("88-item tracker", "83-item tracker")
    updated = updated.replace("88-feature tracker", "83-feature tracker")
    updated = updated.replace("all 88 roadmap statuses", "all 83 roadmap statuses")
    if p.name == "ROADMAP_88_NEXT_BATCH.md":
        updated = "\n".join(line for line in updated.split("\n")
                            if not ("Windows desktop architecture" in line or
                                    "Android APK is not a Windows executable" in line))
    if p.name == "BATCH_v0134_BOOKS_AND_TABLETS.md":
        updated = updated.replace(" and native Windows", "")
    if p.name == "ENTERTAINMENT_BATCH_v0136.md":
        updated = updated.replace(", true Windows support", "")
    if updated != original:
        p.write_text(updated, encoding="utf-8")
old_batch = docs / "ROADMAP_88_NEXT_BATCH.md"
if old_batch.exists():
    old_batch.rename(docs / "ROADMAP_83_NEXT_BATCH.md")
large = docs / "LARGE_SCREENS_AND_WINDOWS.md"
if large.exists():
    text = large.read_text(encoding="utf-8")
    index = text.find("## Windows: options")
    if index >= 0:
        text = text[:index].rstrip() + "\n"
    text = text.replace("# Large screens and Windows", "# Large-screen Android layouts")
    (docs / "LARGE_SCREENS.md").write_text(text, encoding="utf-8")
    large.unlink()
for p in docs.glob("*.md"):
    text = p.read_text(encoding="utf-8")
    updated = text.replace("LARGE_SCREENS_AND_WINDOWS.md", "LARGE_SCREENS.md")
    updated = updated.replace("ROADMAP_88_NEXT_BATCH.md", "ROADMAP_83_NEXT_BATCH.md")
    if updated != text: p.write_text(updated, encoding="utf-8")
print("Active roadmap migrated; requirements", len(rows), counts)
