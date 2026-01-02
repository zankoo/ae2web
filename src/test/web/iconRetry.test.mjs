import assert from 'node:assert/strict';
import test from 'node:test';

import { nextIconRetry } from '../../main/resources/web/js/iconRetry.js';

test('nextIconRetry ramps up exponentially then keeps retrying at max delay', () => {
    assert.deepEqual(nextIconRetry(0, { baseMs: 1500, maxMs: 30000, maxAttempts: 6 }), { attempts: 1, delayMs: 1500 });
    assert.deepEqual(nextIconRetry(1, { baseMs: 1500, maxMs: 30000, maxAttempts: 6 }), { attempts: 2, delayMs: 3000 });
    assert.deepEqual(nextIconRetry(2, { baseMs: 1500, maxMs: 30000, maxAttempts: 6 }), { attempts: 3, delayMs: 6000 });
    assert.deepEqual(nextIconRetry(3, { baseMs: 1500, maxMs: 30000, maxAttempts: 6 }), { attempts: 4, delayMs: 12000 });
    assert.deepEqual(nextIconRetry(4, { baseMs: 1500, maxMs: 30000, maxAttempts: 6 }), { attempts: 5, delayMs: 24000 });
    assert.deepEqual(nextIconRetry(5, { baseMs: 1500, maxMs: 30000, maxAttempts: 6 }), { attempts: 6, delayMs: 30000 });

    assert.deepEqual(nextIconRetry(6, { baseMs: 1500, maxMs: 30000, maxAttempts: 6 }), { attempts: 6, delayMs: 30000 });
    assert.deepEqual(nextIconRetry(100, { baseMs: 1500, maxMs: 30000, maxAttempts: 6 }), { attempts: 6, delayMs: 30000 });
});
