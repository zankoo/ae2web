import assert from 'node:assert/strict';
import test from 'node:test';

import { computeFlipDelta } from '../../main/resources/web/js/flipAnimation.js';

test('computeFlipDelta returns invert translation from old to new position', () => {
    assert.deepEqual(
        computeFlipDelta({ left: 10, top: 10 }, { left: 20, top: 35 }),
        { dx: -10, dy: -25 }
    );
});
