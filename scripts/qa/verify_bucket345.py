#!/usr/bin/env python3
"""No dependencies. Run in CI before Gradle to catch broken source/UI integration."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re

root = Path(__file__).resolve().parents[2]
res = root / 'app/src/main/res'
source = root / 'app/src/main/kotlin/org/koitharu/kotatsu'
for name in ('values/strings.xml', 'layout/activity_monogatari_book_catalog.xml'):
    ET.parse(res / name)

activity = (source / 'core/media/BookLibraryActivity.kt').read_text()
layout = (res / 'layout/activity_monogatari_book_catalog.xml').read_text()
strings = (res / 'values/strings.xml').read_text()
for name in ('baen', 'external'):
    rid = 'monogatari_catalog_' + name
    assert f'android:id="@+id/{rid}"' in layout, f'Missing catalog control {rid}'
    assert f'R.id.{rid}' in activity, f'Unbound catalog control {rid}'
    assert f'<string name="{rid}">' in strings or name == 'external', f'Missing catalog label {rid}'
for path in ('core/media/OpdsAuth.kt', 'sourcehealth/SourceLogSanitizer.kt',
             'core/ui/MonogatariSectionStyle.kt', 'core/media/BaenFreeLibrary.kt'):
    assert (source / path).is_file(), f'Missing source {path}'
assert 'MonogatariSectionStyle.card' in (source / 'main/ui/MoreActivity.kt').read_text()
assert 'MoreDestinations.filteredSections' in (source / 'main/ui/MoreActivity.kt').read_text()
assert 'OpdsAuth.headerFor' in (source / 'builtin/opds/OpdsRepository.kt').read_text()
assert 'Authenticated OPDS catalogs require HTTPS' in (source / 'core/media/OpdsCatalog.kt').read_text()
assert 'SourceLogSanitizer' in (source / 'sourcehealth/SourceActivityLog.kt').read_text()
assert 'opds_account_privacy' in (source / 'core/qa/MonogatariTestPlan.kt').read_text()

# Secrets only live in app-private preferences/backups, never in code or metadata.
scan = [source / 'core/media/BookLibraryActivity.kt', source / 'core/media/OpdsAuth.kt']
for path in scan:
    text = path.read_text()
    assert not re.search(r'(?i)(client_secret|access_token|api_key)\s*=\s*"[A-Za-z0-9_-]{20,}"', text), path
print('PASS: XML, source wiring, private credential boundaries, QA cases, CI guards')
