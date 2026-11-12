(function (root) {
  'use strict';

  const validTypes = new Set(['deposit', 'withdraw']);

  function sanitizeTransactions(value) {
    if (!Array.isArray(value)) return null;
    return value.reduce((clean, item, index) => {
      if (!item || !validTypes.has(item.type)) return clean;
      if (!Number.isSafeInteger(item.amount) || item.amount <= 0) return clean;
      if (!Number.isSafeInteger(item.createdAt) || item.createdAt < 0) return clean;
      if (!Number.isFinite(new Date(item.createdAt).getTime())) return clean;

      clean.push({
        id: typeof item.id === 'string' && item.id.length > 0 && item.id.length <= 128
          ? item.id
          : `restored-${item.createdAt}-${index}`,
        type: item.type,
        amount: item.amount,
        note: typeof item.note === 'string' ? item.note.slice(0, 80) : '',
        createdAt: item.createdAt,
      });
      return clean;
    }, []);
  }

  function totals(transactions) {
    return transactions.reduce((result, transaction) => {
      result[transaction.type === 'deposit' ? 'income' : 'expense'] += transaction.amount;
      return result;
    }, { income: 0, expense: 0 });
  }

  function canWithdraw(transactions, amount) {
    if (!Number.isSafeInteger(amount) || amount <= 0) return false;
    const { income, expense } = totals(transactions);
    return amount <= income - expense;
  }

  root.QNCWalletCore = Object.freeze({ sanitizeTransactions, totals, canWithdraw });
})(globalThis);
