// اختبار دخان سريع لمنطق التطبيق عبر jsdom
import { JSDOM } from 'jsdom';
import fs from 'node:fs';
import assert from 'node:assert/strict';
const html = fs.readFileSync('www/index.html', 'utf8').replace(/<script[^>]*><\/script>/g, '').replace(/<link rel="stylesheet"[^>]*>/, '');
const errors = [];
const dom = new JSDOM(html, { url: 'https://localhost/?nosplash', runScripts: 'outside-only', pretendToBeVisual: true });
const w = dom.window;
w.matchMedia = () => ({ matches: false });
w.addEventListener('error', e => errors.push(e.error || e.message));
w.eval(fs.readFileSync('www/vendor/xlsx.mini.min.js', 'utf8'));
w.eval(fs.readFileSync('www/js/app.js', 'utf8'));
const sleep = ms => new Promise(r => setTimeout(r, ms));
const $ = s => w.document.querySelector(s);
const click = s => { const el = typeof s === 'string' ? $(s) : s; assert.ok(el, 'missing ' + s); el.dispatchEvent(new w.MouseEvent('click', { bubbles: true })); };
const type = (s, v) => { const el = $(s); el.value = v; el.dispatchEvent(new w.Event('input', { bubbles: true })); };
await sleep(100);
assert.equal($('#app').hidden, false);
// إيداع
click('[data-act=newTx][data-type=dep]'); type('#fAmount', '١٢٥٠٠٠٠');
assert.equal($('#fAmount').value, '1,250,000');
$('#fNote').value = 'مبيعات <b>اليوم</b>'; click('#fCats .chip'); click('#fSave'); await sleep(20);
assert.equal(w.QnS.balance(), 1250000);
assert.match($('.balance .amt').textContent, /1,250,000/);
assert.ok(!$('#view').innerHTML.includes('<b>اليوم</b>'), 'escaped');
// سحب أكبر من الرصيد
click('[data-act=newTx][data-type=wd]'); type('#fAmount', '2000000');
assert.ok($('#fPreview .preview').classList.contains('bad')); assert.ok($('#fSave').disabled);
click('#fSave'); assert.equal(w.QnS.balance(), 1250000);
type('#fAmount', '250000'); assert.match($('#fPreview .net').textContent, /1,000,000/); assert.ok(!$('#fSave').disabled);
click('#fSave'); await sleep(20);
assert.equal(w.QnS.balance(), 1000000);
const t = w.QnS.totals(w.QnS.state.tx); assert.deepEqual([t.dep, t.wd], [1250000, 250000]);
// السجل والفلترة
click('[data-act=nav][data-v=history]'); assert.equal(w.document.querySelectorAll('#hlist .tx').length, 2);
click('[data-act=filter][data-g=ftype][data-v=wd]'); assert.equal(w.document.querySelectorAll('#hlist .tx').length, 1);
click('[data-act=filter][data-g=ftype][data-v=all]'); click('[data-act=filter][data-g=period][data-v=day]');
assert.equal(w.document.querySelectorAll('#hlist .tx').length, 2);
type('#q', 'مبيعات'); assert.equal(w.document.querySelectorAll('#hlist .tx').length, 1);
type('#q', '');
// تعديل + سجل تعديلات
click('#hlist .tx.wd'); click('[data-act=editTx]'); type('#fAmount', '300000'); click('#fSave'); await sleep(20);
assert.equal(w.QnS.balance(), 950000); assert.equal(w.QnS.state.log.length, 1);
// منع حذف إيداع يجعل الرصيد سالباً
click('#hlist .tx.dep'); click('[data-act=delTx]'); await sleep(20);
assert.match($('#dialog h3').textContent, /لا يمكن الحذف/); click('[data-act=dlg][data-v="1"]');
// حذف السحب مع تأكيد
click('[data-act=closeSheet]'); click('#hlist .tx.wd'); click('[data-act=delTx]'); await sleep(20);
click('[data-act=dlg][data-v="0"]'); await sleep(20); assert.equal(w.QnS.state.tx.length, 2);
click('[data-act=delTx]'); await sleep(20); click('[data-act=dlg][data-v="1"]'); await sleep(20);
assert.equal(w.QnS.state.tx.length, 1); assert.equal(w.QnS.balance(), 1250000);
// محفظة ثانية برصيد مستقل
click('[data-act=wallets]'); click('[data-act=addWallet]'); await sleep(80); $('#dlgInput').value = 'الادخار'; click('[data-act=dlg][data-v="1"]'); await sleep(20);
assert.equal(w.QnS.state.wallets.length, 2); assert.equal(w.QnS.balance(), 0);
click('[data-act=closeSheet]');
// بقية الشاشات تُعرض دون أخطاء
click('[data-act=nav][data-v=reports]'); assert.ok($('.chart'));
click('[data-act=nav][data-v=settings]'); click('.switch[data-act=theme]'); assert.equal(w.document.documentElement.dataset.theme, 'light');
click('[data-act=goal]'); type('#gTarget', '5000000'); click('[data-act=saveGoal]');
click('.switch[data-act=pin]'); $('#pNew').value = '1234'; $('#pNew2').value = '1234'; click('[data-act=savePin]'); await sleep(50);
assert.equal(w.QnS.state.settings.pin.len, 4);
click('[data-act=nav][data-v=home]'); assert.ok($('.goal'));
// الحفظ
assert.equal(JSON.parse(w.localStorage.getItem('qns.data.v1')).tx.length, 1);
// PDF
const pdf = Buffer.from(w.QnS.buildPdf([new Uint8Array([1, 2, 3])], 10, 10)).toString('latin1');
assert.ok(pdf.startsWith('%PDF-1.4') && pdf.endsWith('%%EOF'));
const xo = +pdf.match(/startxref\n(\d+)/)[1]; assert.equal(pdf.slice(xo, xo + 4), 'xref');
assert.deepEqual(errors, []);
console.log('✔ كل الاختبارات نجحت');
w.close();
