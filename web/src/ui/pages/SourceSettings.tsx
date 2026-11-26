import { useEffect, useState } from 'preact/hooks';
import { getExtension } from '../../core/extensions';
import { db } from '../../core/db';
import { Page, Spinner, ErrorBox } from '../components';

/** Renders `getSourcePreferences()` (text, switch, checkbox, single and multi choice), like Mangayomi's source settings. */
export function SourceSettingsPage({ sourceId }: { sourceId: number }) {
	const [prefs, setPrefs] = useState<any[] | null>(null);
	const [values, setValues] = useState<Record<string, string>>({});
	const [error, setError] = useState('');

	useEffect(() => {
		void (async () => {
			try {
				const ext = await getExtension(sourceId);
				const list = await ext.getSourcePreferences();
				const stored = await db.entriesWithPrefix<string>('prefs', `${sourceId}:`);
				setValues(Object.fromEntries(stored.map(([k, v]) => [k.slice(String(sourceId).length + 1), String(v)])));
				setPrefs(list);
			} catch (e: any) {
				setError(e?.message ?? String(e));
			}
		})();
	}, [sourceId]);

	const set = (key: string, value: string) => {
		setValues((v) => ({ ...v, [key]: value }));
		void getExtension(sourceId).then((e) => e.setPreference(key, value));
	};

	if (error) return <ErrorBox error={error} />;
	if (!prefs) return <Spinner />;
	return (
		<Page title="Source settings">
			{prefs.length === 0 && <p class="muted">This source has no settings.</p>}
			<p class="muted">Changes apply the next time the source is opened.</p>
			{prefs.map((p, i) => {
				const key: string = p.key;
				const list = p.listPreference;
				const sw = p.switchPreferenceCompat ?? p.checkBoxPreference;
				const text = p.editTextPreference;
				const multi = p.multiSelectListPreference;
				if (list)
					return (
						<label class="field" key={i}>
							<span>{list.title}</span>
							<select class="input" value={values[key] ?? list.entryValues?.[list.valueIndex ?? 0]} onChange={(e) => set(key, (e.target as HTMLSelectElement).value)}>
								{(list.entries ?? []).map((name: string, j: number) => (
									<option value={list.entryValues?.[j]}>{name}</option>
								))}
							</select>
							{list.summary && <small class="muted">{list.summary}</small>}
						</label>
					);
				if (sw)
					return (
						<label class="check" key={i}>
							<input type="checkbox" checked={(values[key] ?? String(!!sw.value)) === 'true'} onChange={(e) => set(key, String((e.target as HTMLInputElement).checked))} />
							<span>
								{sw.title}
								{sw.summary && <small class="muted"> — {sw.summary}</small>}
							</span>
						</label>
					);
				if (text)
					return (
						<label class="field" key={i}>
							<span>{text.title}</span>
							<input class="input" value={values[key] ?? text.value ?? ''} onChange={(e) => set(key, (e.target as HTMLInputElement).value)} />
							{text.summary && <small class="muted">{text.summary}</small>}
						</label>
					);
				if (multi) {
					let current: string[] = multi.values ?? [];
					try {
						if (values[key]) current = JSON.parse(values[key]);
					} catch {
						/* use declared defaults */
					}
					return (
						<fieldset key={i}>
							<legend>{multi.title}</legend>
							{(multi.entries ?? []).map((name: string, j: number) => {
								const v = multi.entryValues?.[j];
								return (
									<label class="check" key={j}>
										<input type="checkbox" checked={current.includes(v)} onChange={(e) => set(key, JSON.stringify((e.target as HTMLInputElement).checked ? [...current, v] : current.filter((x) => x !== v)))} />
										<span>{name}</span>
									</label>
								);
							})}
						</fieldset>
					);
				}
				return null;
			})}
		</Page>
	);
}
