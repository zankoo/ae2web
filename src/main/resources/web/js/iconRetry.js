export function nextIconRetry(attempts, { baseMs = 1500, maxMs = 30000, maxAttempts = 6 } = {}) {
    const base = Number.isFinite(baseMs) && baseMs > 0 ? baseMs : 1500;
    const max = Number.isFinite(maxMs) && maxMs > 0 ? maxMs : 30000;
    const limit = Number.isFinite(maxAttempts) && maxAttempts > 0 ? Math.floor(maxAttempts) : 0;

    const currentAttempts = Number(attempts);
    const safeAttempts = Number.isFinite(currentAttempts) && currentAttempts > 0 ? currentAttempts : 0;

    if (limit > 0 && safeAttempts >= limit) {
        return { attempts: limit, delayMs: max };
    }

    const nextAttempts = safeAttempts + 1;
    const delayMs = Math.min(base * (2 ** (nextAttempts - 1)), max);
    const clampedAttempts = limit > 0 ? Math.min(nextAttempts, limit) : nextAttempts;
    return { attempts: clampedAttempts, delayMs };
}

