const BLOCKED = 'script,style,iframe,frame,object,embed,link,meta,base,form,input,button,textarea,select,svg,math,noscript,template';

/** Novel chapter HTML comes from third-party extensions: keep text, links and images, drop everything active. */
export function sanitizeHtml(html: string): string {
	const doc = new DOMParser().parseFromString(html, 'text/html');
	doc.querySelectorAll(BLOCKED).forEach((n) => n.remove());
	doc.body.querySelectorAll('*').forEach((el) => {
		for (const attr of Array.from(el.attributes)) {
			const name = attr.name.toLowerCase();
			const value = attr.value.trim().toLowerCase();
			if (name.startsWith('on') || name === 'style' || name === 'srcdoc') el.removeAttribute(attr.name);
			else if ((name === 'href' || name === 'src' || name === 'xlink:href') && /^(javascript|data:text|vbscript)/.test(value.replace(/\s/g, ''))) el.removeAttribute(attr.name);
		}
		if (el.tagName === 'A') {
			el.setAttribute('target', '_blank');
			el.setAttribute('rel', 'noreferrer noopener');
		}
		if (el.tagName === 'IMG') {
			el.setAttribute('referrerpolicy', 'no-referrer');
			el.setAttribute('loading', 'lazy');
		}
	});
	return doc.body.innerHTML;
}
