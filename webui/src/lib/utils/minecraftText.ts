function escapeHtml(str: string): string {
	return String(str)
		.replaceAll('&', '&amp;')
		.replaceAll('<', '&lt;')
		.replaceAll('>', '&gt;')
		.replaceAll('"', '&quot;')
		.replaceAll("'", '&#39;');
}

type StyleState = {
	color: string | null;
	bold: boolean;
	italic: boolean;
	underline: boolean;
	strike: boolean;
};

export function legacyTextToHtml(text: string): string {
	const colorMap: Record<string, string> = {
		0: '#000000',
		1: '#0000AA',
		2: '#00AA00',
		3: '#00AAAA',
		4: '#AA0000',
		5: '#AA00AA',
		6: '#FFAA00',
		7: '#AAAAAA',
		8: '#555555',
		9: '#5555FF',
		a: '#55FF55',
		b: '#55FFFF',
		c: '#FF5555',
		d: '#FF55FF',
		e: '#FFFF55',
		f: '#FFFFFF'
	};

	const out: string[] = [];
	let state: StyleState = {
		color: null,
		bold: false,
		italic: false,
		underline: false,
		strike: false
	};
	let buf = '';

	function flush(): void {
		if (!buf) return;
		const safe = escapeHtml(buf);
		const styles: string[] = [];
		if (state.color) styles.push(`color:${state.color}`);
		if (state.bold) styles.push('font-weight:700');
		if (state.italic) styles.push('font-style:italic');
		const decorations: string[] = [];
		if (state.underline) decorations.push('underline');
		if (state.strike) decorations.push('line-through');
		if (decorations.length) styles.push(`text-decoration:${decorations.join(' ')}`);

		if (styles.length) {
			out.push(`<span style="${styles.join(';')}">${safe}</span>`);
		} else {
			out.push(safe);
		}
		buf = '';
	}

	const s = String(text ?? '');
	for (let i = 0; i < s.length; i++) {
		const ch = s[i];
		if (ch !== '§' || i === s.length - 1) {
			buf += ch;
			continue;
		}

		const code = (s[i + 1] || '').toLowerCase();
		i++;
		flush();

		if (code === 'r') {
			state = { color: null, bold: false, italic: false, underline: false, strike: false };
			continue;
		}

		if (code === 'l') {
			state.bold = true;
			continue;
		}
		if (code === 'o') {
			state.italic = true;
			continue;
		}
		if (code === 'n') {
			state.underline = true;
			continue;
		}
		if (code === 'm') {
			state.strike = true;
			continue;
		}
		if (code === 'k') {
			continue;
		}

		if (code === 'x') {
			let hex = '';
			for (let j = 0; j < 6 && i + 2 < s.length; j++) {
				if (s[i + 1] !== '§') break;
				const h = s[i + 2];
				hex += h;
				i += 2;
			}
			if (/^[0-9a-fA-F]{6}$/.test(hex)) {
				state = { color: `#${hex}`, bold: false, italic: false, underline: false, strike: false };
			}
			continue;
		}

		const color = colorMap[code];
		if (color) {
			state = { color, bold: false, italic: false, underline: false, strike: false };
		}
	}

	flush();
	return out.join('');
}
