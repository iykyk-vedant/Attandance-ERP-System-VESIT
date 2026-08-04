// Unit tests for Predictor Core
import assert from 'assert';
import { calculatePredictor } from './index.ts';

console.log('Running Predictor Core Unit Tests...');

// Test 1: Zero total lectures
const test1 = calculatePredictor({ present: 0, total: 0 });
assert.strictEqual(test1.currentPct, 0);
assert.strictEqual(test1.headline, 'No lectures held yet');

// Test 2: Safe skips (above 75%)
// Present 45/50 = 90%. 45 / 0.75 = 60. Max total = 60. Safe skips = 60 - 50 = 10.
const test2 = calculatePredictor({ present: 45, total: 50, threshold: 0.75 });
assert.strictEqual(test2.currentPct, 90);
assert.strictEqual(test2.safeSkips, 10);
assert.strictEqual(test2.mustAttend, 0);

// Test 3: Must attend (below 75%)
// Present 30/50 = 60%. (0.75 * 50 - 30) / 0.25 = (37.5 - 30) / 0.25 = 30.
const test3 = calculatePredictor({ present: 30, total: 50, threshold: 0.75 });
assert.strictEqual(test3.currentPct, 60);
assert.strictEqual(test3.mustAttend, 30);
assert.strictEqual(test3.safeSkips, 0);
assert.strictEqual(test3.isRecoverable, true);

// Test 4: Unrecoverable state
// Present 10/100 = 10%. Remaining planned = 10.
const test4 = calculatePredictor({ present: 10, total: 100, threshold: 0.75, remainingPlanned: 10 });
assert.strictEqual(test4.isRecoverable, false);

console.log('✅ All Predictor Core unit tests passed!');
