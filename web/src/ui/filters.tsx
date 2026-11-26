/**
 * Editor for Mangayomi `getFilterList()` filters. The objects are mutated in place and handed back to `search()`
 * unchanged in shape, exactly like Mangayomi's own UI does.
 */
export function FilterEditor({ filters, onChange }: { filters: any[]; onChange: () => void }) {
	return (
		<div class="filters">
			{filters.map((f, i) => (
				<Filter key={i} f={f} onChange={onChange} />
			))}
		</div>
	);
}

function Filter({ f, onChange }: { f: any; onChange: () => void }) {
	const label = f.name ?? '';
	switch (f.type_name) {
		case 'HeaderFilter':
			return <h4 class="muted">{label}</h4>;
		case 'SeparatorFilter':
			return <hr />;
		case 'SelectFilter':
			return (
				<label class="field">
					<span>{label}</span>
					<select
						class="input"
						value={f.state ?? 0}
						onChange={(e) => {
							f.state = Number((e.target as HTMLSelectElement).value);
							onChange();
						}}
					>
						{(f.values ?? []).map((v: any, i: number) => (
							<option key={i} value={i}>
								{v.name}
							</option>
						))}
					</select>
				</label>
			);
		case 'CheckBox':
			return (
				<label class="check">
					<input
						type="checkbox"
						checked={!!f.state}
						onChange={(e) => {
							f.state = (e.target as HTMLInputElement).checked;
							onChange();
						}}
					/>
					<span>{label}</span>
				</label>
			);
		case 'TriState': {
			const states = ['—', '✓ include', '✕ exclude'];
			return (
				<button
					class="btn small"
					onClick={() => {
						f.state = ((f.state ?? 0) + 1) % 3;
						onChange();
					}}
				>
					{label}: {states[f.state ?? 0]}
				</button>
			);
		}
		case 'TextFilter':
			return (
				<label class="field">
					<span>{label}</span>
					<input
						class="input"
						value={f.state ?? ''}
						onInput={(e) => {
							f.state = (e.target as HTMLInputElement).value;
							onChange();
						}}
					/>
				</label>
			);
		case 'SortFilter': {
			const st = f.state ?? (f.state = { index: 0, ascending: false, type_name: 'SortState' });
			return (
				<div class="field">
					<span>{label}</span>
					<div class="row">
						<select
							class="input grow"
							value={st.index ?? 0}
							onChange={(e) => {
								st.index = Number((e.target as HTMLSelectElement).value);
								onChange();
							}}
						>
							{(f.values ?? []).map((v: any, i: number) => (
								<option key={i} value={i}>
									{v.name}
								</option>
							))}
						</select>
						<button
							class="btn small"
							onClick={() => {
								st.ascending = !st.ascending;
								onChange();
							}}
						>
							{st.ascending ? '↑ Asc' : '↓ Desc'}
						</button>
					</div>
				</div>
			);
		}
		case 'GroupFilter':
			return (
				<fieldset>
					<legend>{label}</legend>
					{(f.state ?? []).map((c: any, i: number) => (
						<Filter key={i} f={c} onChange={onChange} />
					))}
				</fieldset>
			);
		default:
			return null;
	}
}
