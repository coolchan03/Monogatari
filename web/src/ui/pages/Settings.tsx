import { useEffect, useState } from 'preact/hooks';
import { exportBackup, importBackup, wipeAll, type ImportReport } from '../../core/backup';
import { isDesktop } from '../../core/http';
import { installSource, listRepos } from '../../core/repos';
import { DEFAULT_SETTINGS, updateSettings, useSettings, type ReaderMode, type ThemeMode } from '../../core/settings';
import { Page, toast } from '../components';

let deferredInstall: any = null;
addEventListener('beforeinstallprompt', (e) => {
	e.preventDefault();
	deferredInstall = e;
});

const ACCENTS = ['#1976D2', '#7B1FA2', '#C2185B', '#E64A19', '#2E7D32', '#00838F', '#5D4037', '#455A64'];

export function SettingsPage() {
	const s = useSettings();
	const [report, setReport] = useState<ImportReport | null>(null);
	const [busy, setBusy] = useState(false);
	const [storage, setStorage] = useState('');
	const [canInstall, setCanInstall] = useState(!!deferredInstall);

	useEffect(() => {
		void navigator.storage?.estimate?.().then((e) => setStorage(e.usage != null ? `${(e.usage / 1048576).toFixed(1)} MB used of ${((e.quota ?? 0) / 1073741824).toFixed(1)} GB` : ''));
		const f = () => setCanInstall(true);
		addEventListener('beforeinstallprompt', f);
		return () => removeEventListener('beforeinstallprompt', f);
	}, []);

	return (
		<Page title="Settings">
			<section class="settings">
				<h2>Appearance</h2>
				<label class="field">
					<span>Theme</span>
					<select class="input" value={s.theme} onChange={(e) => updateSettings({ theme: (e.target as HTMLSelectElement).value as ThemeMode })}>
						<option value="system">System</option>
						<option value="light">Light</option>
						<option value="dark">Dark</option>
						<option value="amoled">AMOLED black</option>
					</select>
				</label>
				<div class="field">
					<span>Accent colour</span>
					<div class="row wrap">
						{ACCENTS.map((c) => (
							<button key={c} class={'swatch' + (s.accent === c ? ' active' : '')} style={{ background: c }} aria-label={c} onClick={() => updateSettings({ accent: c })} />
						))}
						<input type="color" value={s.accent} onInput={(e) => updateSettings({ accent: (e.target as HTMLInputElement).value })} aria-label="Custom colour" />
					</div>
				</div>
				<label class="field">
					<span>Grid size {s.gridSize}px</span>
					<input type="range" min={100} max={260} step={10} value={s.gridSize} onInput={(e) => updateSettings({ gridSize: Number((e.target as HTMLInputElement).value) })} />
				</label>
				<label class="check">
					<input type="checkbox" checked={s.showNsfw} onChange={(e) => updateSettings({ showNsfw: (e.target as HTMLInputElement).checked })} />
					<span>Show 18+ sources</span>
				</label>

				<h2>Reader</h2>
				<label class="field">
					<span>Default manga mode</span>
					<select class="input" value={s.readerMode} onChange={(e) => updateSettings({ readerMode: (e.target as HTMLSelectElement).value as ReaderMode })}>
						<option value="paged-rtl">Paged · right to left</option>
						<option value="paged-ltr">Paged · left to right</option>
						<option value="webtoon">Webtoon (vertical scroll)</option>
					</select>
				</label>
				<label class="field">
					<span>Pages to preload {s.preloadPages}</span>
					<input type="range" min={0} max={8} value={s.preloadPages} onInput={(e) => updateSettings({ preloadPages: Number((e.target as HTMLInputElement).value) })} />
				</label>

				<h2>Network</h2>
				{isDesktop() ? (
					<p class="muted">The Windows app sends requests from the app itself, so websites never block them with CORS. No proxy is needed.</p>
				) : (
					<>
						<p class="muted">
							Most manga sites do not allow requests from other websites (CORS), so the browser version needs a small relay. Deploy <code>web/proxy/worker.js</code> to a free Cloudflare Worker and paste its URL here (ending in <code>?url=</code>). See <a href="https://github.com/coolchan03/Monogatari/blob/devel/web/README.md" target="_blank" rel="noreferrer noopener">web/README.md</a>.
						</p>
						<label class="field">
							<span>Proxy URL</span>
							<input class="input" placeholder="https://monogatari-proxy.example.workers.dev/?url=" value={s.proxyUrl} onChange={(e) => updateSettings({ proxyUrl: (e.target as HTMLInputElement).value.trim() })} />
						</label>
						<label class="field">
							<span>Use proxy</span>
							<select class="input" value={s.proxyMode} onChange={(e) => updateSettings({ proxyMode: (e.target as HTMLSelectElement).value as any })}>
								<option value="auto">Only when a direct request is blocked</option>
								<option value="always">Always</option>
								<option value="never">Never</option>
							</select>
						</label>
						<label class="check">
							<input type="checkbox" checked={s.proxyImages} onChange={(e) => updateSettings({ proxyImages: (e.target as HTMLInputElement).checked })} />
							<span>Also load images through the proxy (for sites that block hotlinking)</span>
						</label>
					</>
				)}

				<h2>Backup &amp; restore</h2>
				<p class="muted">
					Back up your library, history, bookmarks, categories and installed sources to a file. You can also restore a backup made by the Android app: favourites, history, bookmarks and categories for Mangayomi JavaScript sources carry over.
				</p>
				<div class="row wrap">
					<button
						class="btn primary"
						onClick={async () => {
							const blob = await exportBackup();
							const a = document.createElement('a');
							a.href = URL.createObjectURL(blob);
							a.download = `monogatari-backup-${new Date().toISOString().slice(0, 10)}.json`;
							a.click();
							setTimeout(() => URL.revokeObjectURL(a.href), 5000);
						}}
					>
						Export backup
					</button>
					<label class="btn">
						Restore backup…
						<input
							type="file"
							accept=".json,.zip,application/json,application/zip"
							hidden
							onChange={async (e) => {
								const f = (e.target as HTMLInputElement).files?.[0];
								(e.target as HTMLInputElement).value = '';
								if (!f) return;
								setBusy(true);
								try {
									setReport(await importBackup(f));
								} catch (err: any) {
									toast(err?.message ?? 'Restore failed');
								} finally {
									setBusy(false);
								}
							}}
						/>
					</label>
				</div>
				{busy && <p class="muted">Restoring…</p>}
				{report && (
					<div class="banner column">
						<strong>Restore finished ({report.kind === 'android' ? 'Android backup' : 'web backup'})</strong>
						<div>
							{report.favourites} favourites · {report.history} history · {report.bookmarks} bookmarks · {report.categories} categories
							{report.skipped > 0 ? ` · ${report.skipped} skipped` : ''}
						</div>
						{report.note && <div class="muted">{report.note}</div>}
						{report.missingSources.length > 0 && (
							<>
								<div>{report.missingSources.length} source(s) from the backup are not installed.</div>
								<button
									class="btn primary small"
									onClick={async () => {
										const repos = await listRepos();
										let ok = 0;
										for (const m of report.missingSources) {
											const entry = repos.flatMap((r) => r.entries ?? []).find((e) => e.id === m.id && e.isJs);
											if (!entry) continue;
											try {
												await installSource(entry);
												ok++;
											} catch {
												/* keep going */
											}
										}
										toast(`Installed ${ok} of ${report.missingSources.length} sources`);
									}}
								>
									Install missing sources
								</button>
							</>
						)}
					</div>
				)}

				<h2>App</h2>
				<div class="row wrap">
					{!isDesktop() && canInstall && (
						<button
							class="btn primary"
							onClick={async () => {
								await deferredInstall?.prompt();
								deferredInstall = null;
								setCanInstall(false);
							}}
						>
							Install Monogatari
						</button>
					)}
					<a class="btn" href="#/bookmarks">Bookmarks</a>
					<a class="btn" href="#/repos">Source repositories</a>
					<button
						class="btn danger"
						onClick={async () => {
							if (!confirm('Delete ALL local data (library, history, sources, downloads, settings)? This cannot be undone.')) return;
							await wipeAll();
							updateSettings({ ...DEFAULT_SETTINGS });
							toast('All data deleted');
						}}
					>
						Delete all data
					</button>
				</div>
				<p class="muted">
					Monogatari {isDesktop() ? 'for Windows' : 'web'} · based on Kotatsu-Redo and inspired by Mangayomi. {storage}
				</p>
			</section>
		</Page>
	);
}
