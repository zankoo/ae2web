/**
 * Debounce a function call.
 */
export function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

export function escapeHtml(text) {
    if (!text) return '';
    return text.toString()
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

export function showToast(msg, type) {
    const container = document.getElementById('toast-container');
    const el = document.createElement('div');
    el.className = `toast ${type || ''}`;
    el.innerText = msg;
    container.appendChild(el);
    setTimeout(() => el.remove(), 4000);
}

/**
 * Check if string contains latin letters (indicates pinyin input)
 */
function containsLatin(str) {
    return /[a-zA-Z]/.test(str);
}

export function matchItem(itemName, searchTerm) {
    if (!searchTerm) return true;

    const lowerName = itemName.toLowerCase();
    const lowerSearch = searchTerm.toLowerCase();

    if (lowerName.includes(lowerSearch)) {
        return true;
    }

    if (containsLatin(searchTerm) && typeof window.PinyinMatch !== 'undefined') {
        return !!window.PinyinMatch.match(itemName, searchTerm);
    }

    return false;
}

export function formatAmount(num) {
    if (num >= 1e18) return (num / 1e18).toFixed(1).replace('.0', '') + 'E';
    if (num >= 1e15) return (num / 1e15).toFixed(1).replace('.0', '') + 'P';
    if (num >= 1e12) return (num / 1e12).toFixed(1).replace('.0', '') + 'T';
    if (num >= 1e9) return (num / 1e9).toFixed(1).replace('.0', '') + 'G';
    if (num >= 1e6) return (num / 1e6).toFixed(1).replace('.0', '') + 'M';
    if (num >= 1e3) return (num / 1e3).toFixed(1).replace('.0', '') + 'K';
    return num;
}

export function formatNumber(num) {
    return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

