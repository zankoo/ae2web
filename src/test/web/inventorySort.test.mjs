import assert from 'node:assert/strict';
import test from 'node:test';

import { sortInventoryItems } from '../../main/resources/web/js/inventorySort.js';

test('sortInventoryItems(amount) sorts by amount desc then name asc', () => {
    const items = [
        { id: 'minecraft:diamond', name: 'Diamond', amount: 5, uniqueId: 'd' },
        { id: 'minecraft:arrow', name: 'Arrow', amount: 10, uniqueId: 'a' },
        { id: 'minecraft:apple', name: 'Apple', amount: 10, uniqueId: 'b' },
        { id: 'minecraft:beef', name: '', amount: 12, uniqueId: 'c' },
    ];

    const sorted = sortInventoryItems(items, 'amount');
    assert.deepEqual(sorted.map(x => x.id), [
        'minecraft:beef',
        'minecraft:apple',
        'minecraft:arrow',
        'minecraft:diamond',
    ]);
});

test('sortInventoryItems(name) sorts by name asc then amount desc', () => {
    const items = [
        { id: 'minecraft:diamond', name: 'Diamond', amount: 5, uniqueId: 'd' },
        { id: 'minecraft:beef', name: '', amount: 12, uniqueId: 'c' },
        { id: 'minecraft:arrow', name: 'Arrow', amount: 10, uniqueId: 'a' },
        { id: 'minecraft:apple', name: 'Apple', amount: 10, uniqueId: 'b' },
        { id: 'minecraft:emerald', name: '§aEmerald', amount: 1, uniqueId: 'e' },
    ];

    const sorted = sortInventoryItems(items, 'name');
    assert.deepEqual(sorted.map(x => x.id), [
        'minecraft:apple',
        'minecraft:arrow',
        'minecraft:beef',
        'minecraft:diamond',
        'minecraft:emerald',
    ]);
});
