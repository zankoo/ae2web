export function arrayMove<T>(items: T[], fromIndex: number, toIndex: number): T[] {
	if (!Array.isArray(items) || items.length === 0) return [];

	const from = Math.trunc(fromIndex);
	const to = Math.trunc(toIndex);

	if (from < 0 || from >= items.length) return items;
	if (to < 0 || to >= items.length) return items;
	if (from === to) return items;

	const copy = items.slice();
	const [moved] = copy.splice(from, 1);
	copy.splice(to, 0, moved as T);
	return copy;
}

