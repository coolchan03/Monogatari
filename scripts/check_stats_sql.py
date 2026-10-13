"""Smoke-check the exact Room stats queries using stdlib sqlite3; no Android SDK needed."""
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
DAO = ROOT / "app/src/main/kotlin/org/koitharu/kotatsu/stats/data/StatsDao.kt"
text = DAO.read_text(encoding="utf-8")
query_pattern = re.compile(
    r'@Query\(\s*"""(.*?)"""\s*\)\s*(?:protected\s+)?abstract suspend fun (\w+)',
    re.DOTALL,
)
queries = {name: sql.strip() for sql, name in query_pattern.findall(text)}
for required in ("getTagDurations", "getCachedBacklogChapters", "statsPage", "chapterMetaPage"):
    assert required in queries, f"Missing SQL query for {required}"

db = sqlite3.connect(":memory:")
db.row_factory = sqlite3.Row
db.executescript("""
CREATE TABLE stats(manga_id INTEGER, started_at INTEGER, duration INTEGER, pages INTEGER);
CREATE TABLE tags(tag_id INTEGER, title TEXT);
CREATE TABLE manga_tags(manga_id INTEGER, tag_id INTEGER);
CREATE TABLE manga(manga_id INTEGER, source TEXT);
CREATE TABLE chapters(manga_id INTEGER, chapter_id INTEGER, branch TEXT, `index` INTEGER);
CREATE TABLE chapter_meta(manga_id INTEGER, chapter_id INTEGER, pages INTEGER, words INTEGER, duration_ms INTEGER);
CREATE TABLE history(manga_id INTEGER, chapter_id INTEGER, percent REAL, deleted_at INTEGER);
CREATE TABLE favourites(manga_id INTEGER, deleted_at INTEGER);
INSERT INTO stats VALUES (1, 1000, 100, 2), (2, 1000, 70, 4), (1, 1001, 50, 2);
INSERT INTO tags VALUES (1,'BL'), (2,'bl'), (3,'Romance');
INSERT INTO manga_tags VALUES (1,1), (1,2), (1,3), (2,1);
INSERT INTO manga VALUES (1,'MANGA'), (2,'VIDEO');
INSERT INTO chapters VALUES (1,101,'a',0), (1,102,'a',1), (2,201,NULL,0);
INSERT INTO chapter_meta VALUES (1,101,12,0,0), (1,102,20,0,0), (2,201,0,0,120000);
INSERT INTO history VALUES (1,102,0.75,0);
INSERT INTO favourites VALUES (1,0), (1,0), (2,0);
""")
rows = db.execute(queries["getTagDurations"],
    {"fromDate": 0, "through": 2000, "limit": 10}).fetchall()
totals = {r["title"].strip().lower(): r["durationMs"] for r in rows}
assert totals == {"bl": 220, "romance": 150}, totals
chapters = db.execute(queries["getCachedBacklogChapters"], {"limit": 20}).fetchall()
assert len(chapters) == 3, len(chapters)
assert [r["chapterId"] for r in chapters] == [101, 102, 201]
assert chapters[1]["progress"] == 0.75
assert chapters[2]["durationMs"] == 120000

# Multiple titles can share an identical session timestamp; no OFFSET duplicates.
cursor = (-1, -1)
seen = []
while True:
    page = db.execute(queries["statsPage"],
        {"afterStarted": cursor[0], "afterManga": cursor[1], "limit": 2}).fetchall()
    if not page:
        break
    seen.extend((row["started_at"], row["manga_id"]) for row in page)
    cursor = seen[-1]
assert seen == [(1000, 1), (1000, 2), (1001, 1)], seen

cursor = (-1, -1)
seen = []
while True:
    page = db.execute(queries["chapterMetaPage"],
        {"afterManga": cursor[0], "afterChapter": cursor[1], "limit": 1}).fetchall()
    if not page:
        break
    seen.extend((row["manga_id"], row["chapter_id"]) for row in page)
    cursor = seen[-1]
assert seen == [(1, 101), (1, 102), (2, 201)], seen
print("PASS: tag deduplication, cached backlog, stats and chapter-meta cursor pagination")
