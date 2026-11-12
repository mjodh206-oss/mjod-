const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync(require('node:path').join(__dirname, '..', 'wallet-core.js'), 'utf8');
const sandbox = {};
vm.runInNewContext(source, sandbox);
const core = sandbox.QNCWalletCore;
const baseTime = Date.UTC(2026, 10, 9);

const deposit = (amount, id = 'd1') => ({ id, type: 'deposit', amount, note: '', createdAt: baseTime });
const withdrawal = (amount, id = 'w1') => ({ id, type: 'withdraw', amount, note: '', createdAt: baseTime + 1 });

test('computes the deposit, withdrawal and remaining-balance totals', () => {
  assert.deepEqual({ ...core.totals([deposit(120_000), deposit(30_000, 'd2'), withdrawal(25_000)]) }, {
    income: 150_000,
    expense: 25_000,
  });
});

test('allows a withdrawal up to the available balance, but never above it', () => {
  const entries = [deposit(80_000), withdrawal(20_000)];
  assert.equal(core.canWithdraw(entries, 60_000), true);
  assert.equal(core.canWithdraw(entries, 60_001), false);
  assert.equal(core.canWithdraw(entries, 0), false);
  assert.equal(core.canWithdraw(entries, 1.5), false);
  assert.equal(core.canWithdraw([], 1), false);
});

test('rejects malformed backups and sanitizes bad transaction records', () => {
  assert.equal(core.sanitizeTransactions(null), null);
  assert.equal(core.sanitizeTransactions('{}'), null);
  const cleaned = core.sanitizeTransactions([
    deposit(1_000),
    { ...deposit(20), amount: Number.MAX_SAFE_INTEGER + 1 },
    { ...deposit(30), createdAt: 'not-a-date' },
    { ...deposit(40), type: 'transfer' },
    { ...deposit(50), note: 123, id: '' },
  ]);
  assert.equal(cleaned.length, 2);
  assert.equal(cleaned[1].note, '');
  assert.equal(cleaned[1].id, `restored-${baseTime}-${4}`);
});

test('limits restored notes and identifiers to safe sizes', () => {
  const cleaned = core.sanitizeTransactions([{
    ...deposit(4),
    id: 'i'.repeat(129),
    note: 'ن'.repeat(100),
  }]);
  assert.equal(cleaned[0].id.length <= 128, true);
  assert.equal(cleaned[0].note.length, 80);
});
