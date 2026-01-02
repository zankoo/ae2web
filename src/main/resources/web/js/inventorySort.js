const LEGACY_FORMATTING_CODE = /\u00A7[0-9A-FK-OR]/gi;
const DEFAULT_COLLATOR = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' });

function stripLegacyFormatting(text) {
    return String(text).replace(LEGACY_FORMATTING_CODE, '');
}

function getDisplayName(item) {
    const name = item?.name;
    if (typeof name === 'string' && name.trim()) {
        return stripLegacyFormatting(name).trim();
    }

    const id = item?.id;
    if (typeof id === 'string' && id.includes(':')) {
        const [, path] = id.split(':', 2);
        return path || id;
    }

    if (typeof id === 'string') {
        return id;
    }

    return '';
}

function getAmount(item) {
    const amount = Number(item?.amount);
    return Number.isFinite(amount) ? amount : 0;
}

function compareStableKey(a, b) {
    const keyA = a?.uniqueId ?? a?.id ?? '';
    const keyB = b?.uniqueId ?? b?.id ?? '';
    return DEFAULT_COLLATOR.compare(String(keyA), String(keyB));
}

export function sortInventoryItems(items, sortKey) {
    const list = Array.isArray(items) ? items.slice() : [];
    const mode = typeof sortKey === 'string' ? sortKey.toLowerCase() : 'amount';

    list.sort((a, b) => {
        if (mode === 'name') {
            const nameCmp = DEFAULT_COLLATOR.compare(getDisplayName(a), getDisplayName(b));
            if (nameCmp !== 0) return nameCmp;

            const amountDiff = getAmount(b) - getAmount(a);
            if (amountDiff !== 0) return amountDiff;

            return compareStableKey(a, b);
        }

        const amountDiff = getAmount(b) - getAmount(a);
        if (amountDiff !== 0) return amountDiff;

        const nameCmp = DEFAULT_COLLATOR.compare(getDisplayName(a), getDisplayName(b));
        if (nameCmp !== 0) return nameCmp;

        return compareStableKey(a, b);
    });

    return list;
}
