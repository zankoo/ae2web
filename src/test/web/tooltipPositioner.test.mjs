import assert from 'node:assert/strict';
import test from 'node:test';

import { computeTooltipPosition, createTooltipPositioner } from '../../main/resources/web/js/tooltipPositioner.js';

function createFakeRaf() {
    let nextId = 1;
    const queue = new Map();

    function requestAnimationFrame(callback) {
        const id = nextId;
        nextId += 1;
        queue.set(id, callback);
        return id;
    }

    function cancelAnimationFrame(id) {
        queue.delete(id);
    }

    function flush() {
        const callbacks = Array.from(queue.values());
        queue.clear();
        callbacks.forEach((callback) => callback(0));
    }

    return { requestAnimationFrame, cancelAnimationFrame, flush };
}

test('computeTooltipPosition places tooltip near cursor with overflow handling', () => {
    assert.deepEqual(
        computeTooltipPosition({
            x: 50,
            y: 60,
            tooltipWidth: 100,
            tooltipHeight: 40,
            viewportWidth: 800,
            viewportHeight: 600,
            padding: 12,
            margin: 10,
        }),
        { left: 60, top: 70 }
    );

    assert.deepEqual(
        computeTooltipPosition({
            x: 190,
            y: 190,
            tooltipWidth: 100,
            tooltipHeight: 50,
            viewportWidth: 200,
            viewportHeight: 200,
            padding: 12,
            margin: 10,
        }),
        { left: 80, top: 130 }
    );
});

test('createTooltipPositioner throttles updates and caches tooltip size', () => {
    const raf = createFakeRaf();

    const applied = [];
    let tooltipSizeCalls = 0;

    const positioner = createTooltipPositioner({
        requestAnimationFrame: raf.requestAnimationFrame,
        cancelAnimationFrame: raf.cancelAnimationFrame,
        getTooltipSize: () => {
            tooltipSizeCalls += 1;
            return { width: 100, height: 50 };
        },
        getViewportSize: () => ({ width: 200, height: 200 }),
        applyPosition: (left, top) => applied.push({ left, top }),
        padding: 12,
        margin: 10,
    });

    positioner.onMouseMove(20, 20);
    positioner.onMouseMove(30, 30);
    positioner.onMouseMove(40, 40);
    raf.flush();

    assert.equal(applied.length, 1);
    assert.deepEqual(applied[0], { left: 50, top: 50 });
    assert.equal(tooltipSizeCalls, 1);

    positioner.onMouseMove(190, 190);
    raf.flush();
    assert.equal(applied.length, 2);
    assert.deepEqual(applied[1], { left: 80, top: 130 });
    assert.equal(tooltipSizeCalls, 1);

    positioner.invalidateSize();
    positioner.onMouseMove(190, 190);
    raf.flush();
    assert.equal(applied.length, 3);
    assert.equal(tooltipSizeCalls, 2);

    positioner.onMouseMove(20, 20);
    positioner.cancel();
    raf.flush();
    assert.equal(applied.length, 3);
});
