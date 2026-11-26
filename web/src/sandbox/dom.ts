import { elementText, select, selectFirst } from './selector';

/**
 * Host side of Mangayomi's Document/Element shim. JS holds elements as integer keys; a missing element is a key
 * mapped to null and every accessor on it answers "" / false instead of throwing, as upstream does.
 */
export class DomBridge {
	private elements = new Map<number, Element | null>();
	private next = 0;
	private docs = new Map<string, Document>();

	reset() {
		this.elements.clear();
		this.docs.clear();
	}

	private doc(html: string): Document {
		let d = this.docs.get(html);
		if (d) {
			this.docs.delete(html);
			this.docs.set(html, d);
			return d;
		}
		d = new DOMParser().parseFromString(html, 'text/html');
		this.docs.set(html, d);
		if (this.docs.size > 8) this.docs.delete(this.docs.keys().next().value as string);
		return d;
	}

	private put(el: Element | null | undefined): number {
		this.next++;
		this.elements.set(this.next, el ?? null);
		return this.next;
	}

	private putAll(list: Iterable<Element> | null | undefined): string {
		return JSON.stringify(Array.from(list ?? []).map((e) => this.put(e)));
	}

	private el(key: unknown): Element | null {
		return typeof key === 'number' ? (this.elements.get(key) ?? null) : null;
	}

	/** @returns undefined when `name` is not a DOM call. */
	handle(name: string, a: any[]): unknown {
		switch (name) {
			case 'get_doc_element': {
				const d = this.doc(a[0] ?? '');
				switch (a[1]) {
					case 'body':
						return this.put(d.body);
					case 'documentElement':
						return this.put(d.documentElement);
					case 'head':
						return this.put(d.head);
					default:
						return this.put(null);
				}
			}
			case 'get_doc_string':
				return a[1] === 'text' ? '' : this.doc(a[0] ?? '').documentElement.outerHTML;
			case 'get_element_string':
				return this.elementString(a[0], this.el(a[1]));
			case 'doc_select_first':
				return this.put(selectFirst(this.doc(a[0] ?? ''), a[1] ?? ''));
			case 'ele_selectFirst': {
				const e = this.el(a[1]);
				return this.put(e ? selectFirst(e, a[0] ?? '') : null);
			}
			case 'ele_element_sibling': {
				const e = this.el(a[1]);
				return this.put(a[0] === 'nextElementSibling' ? e?.nextElementSibling : e?.previousElementSibling);
			}
			case 'ele_attr':
				return this.el(a[1])?.getAttribute(a[0] ?? '') ?? '';
			case 'doc_attr':
				return '';
			case 'ele_has_attr':
				return this.el(a[1])?.hasAttribute(a[0] ?? '') ?? false;
			case 'doc_has_attr':
				return false;
			case 'doc_xpath_first':
				return this.xpathValues(this.doc(a[0] ?? ''), a[1] ?? '')[0] ?? '';
			case 'ele_xpathFirst':
			case 'xpathFirst': {
				const e = this.el(a[1]);
				return e ? (this.xpathValues(e, a[0] ?? '')[0] ?? '') : '';
			}
			case 'doc_xpath':
				return this.xpathList(this.doc(a[0] ?? ''), a[1] ?? '');
			case 'ele_xpath':
			case 'xpath': {
				const e = this.el(a[1]);
				return e ? this.xpathList(e, a[0] ?? '') : '[]';
			}
			case 'doc_get_elements_by': {
				const d = this.doc(a[0] ?? '');
				switch (a[1]) {
					case 'children':
						return this.putAll(d.children);
					case 'getElementsByTagName':
						return this.putAll(d.getElementsByTagName(a[2] ?? ''));
					default:
						return this.putAll(d.getElementsByClassName(a[2] ?? ''));
				}
			}
			case 'ele_get_elements_by': {
				const e = this.el(a[2]);
				if (!e) return '[]';
				switch (a[0]) {
					case 'children':
						return this.putAll(e.children);
					case 'getElementsByTagName':
						return this.putAll(e.getElementsByTagName(a[1] ?? ''));
					default:
						return this.putAll(e.getElementsByClassName(a[1] ?? ''));
				}
			}
			case 'doc_get_element_by_id':
				return this.put(this.doc(a[0] ?? '').getElementById(a[1] ?? ''));
			case 'doc_select':
				return this.putAll(select(this.doc(a[0] ?? ''), a[1] ?? ''));
			case 'ele_select': {
				const e = this.el(a[1]);
				return this.putAll(e ? select(e, a[0] ?? '') : []);
			}
			default:
				return undefined;
		}
	}

	private elementString(type: string, e: Element | null): string {
		if (!e) return '';
		const first = (re: RegExp) => re.exec(e.outerHTML)?.[1] ?? '';
		switch (type) {
			case 'text':
				return e.textContent ?? '';
			case 'innerHtml':
				return e.innerHTML;
			case 'outerHtml':
				return e.outerHTML;
			case 'className':
				return e.className;
			case 'localName':
				return e.localName;
			case 'namespaceUri':
				return 'http://www.w3.org/1999/xhtml';
			case 'getSrc':
				return first(/src="([^"]+)"/);
			case 'getImg':
				return first(/img="([^"]+)"/);
			case 'getHref':
				return first(/href="([^"]+)"/);
			default:
				return first(/data-src="([^"]+)"/);
		}
	}

	// A trailing `/@attr` or `/text()` step is applied here (like the Android implementation) so attribute values and
	// text come back as strings; plain element paths return the element text.
	private xpathValues(root: Node, xpath: string): string[] {
		try {
			const attr = /\/@([\w:-]+)$/.exec(xpath);
			const isText = xpath.endsWith('/text()');
			let path = attr ? xpath.slice(0, attr.index) : isText ? xpath.slice(0, -'/text()'.length) : xpath;
			if (!path) path = '.';
			const ownerDoc = root.nodeType === 9 ? (root as Document) : root.ownerDocument!;
			const snap = ownerDoc.evaluate(path, root, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
			const out: string[] = [];
			for (let i = 0; i < snap.snapshotLength; i++) {
				const node = snap.snapshotItem(i) as Element;
				if (attr) {
					if (node.hasAttribute?.(attr[1])) out.push((node.getAttribute(attr[1]) ?? '').trim());
				} else if (isText) {
					out.push(
						Array.from(node.childNodes)
							.filter((n) => n.nodeType === 3)
							.map((n) => n.textContent)
							.join('')
							.trim(),
					);
				} else out.push(elementText(node));
			}
			return out;
		} catch {
			return [];
		}
	}

	private xpathList(root: Node, xpath: string): string {
		const found = this.xpathValues(root, xpath);
		// upstream returns [] unless more than one node matched
		return JSON.stringify(found.length > 1 ? found : []);
	}
}
