import Pinyin from 'pinyin-match';

function containsLatin(str: string): boolean {
	return /[a-zA-Z]/.test(str);
}

export function matchItem(itemName: string, searchTerm: string): boolean {
	if (!searchTerm) return true;

	const lowerName = itemName.toLowerCase();
	const lowerSearch = searchTerm.toLowerCase();

	if (lowerName.includes(lowerSearch)) {
		return true;
	}

	if (containsLatin(searchTerm)) {
		return Boolean(Pinyin.match(itemName, searchTerm));
	}

	return false;
}
