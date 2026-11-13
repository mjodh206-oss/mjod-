const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM, VirtualConsole} = require('jsdom');
function app(){
  const errors=[]; const virtualConsole=new VirtualConsole();
  virtualConsole.on('jsdomError',e=>errors.push(e));
  const html=fs.readFileSync('www/index.html','utf8').replace(/<script src="[^"]+"><\/script>/g,'');
  const dom=new JSDOM(html,{url:'http://localhost:3000',runScripts:'dangerously',virtualConsole});
  for(const f of ['core.js','data.js','audio-engine.js','app.js'])dom.window.eval(fs.readFileSync('www/'+f,'utf8'));
  return {dom, doc:dom.window.document, errors};
}
test('three destinations lead to all secondary screens',()=>{
  const {dom,doc,errors}=app();
  try {
    assert.equal(doc.querySelectorAll('.bottom-nav .nav-item').length,3);
    doc.querySelector('.bottom-nav [data-nav-tab="more"]').click();
    assert.equal(doc.querySelector('[data-tab-panel="more"]').hidden,false);
    doc.querySelector('[data-tab-panel="more"] [data-nav-tab="games"]').click();
    assert.equal(doc.querySelector('[data-tab-panel="games"]').hidden,false);
    assert.equal(doc.querySelector('.bottom-nav [data-nav-tab="more"]').getAttribute('aria-current'),'page');
    doc.querySelector('.bottom-nav [data-nav-tab="more"]').click();
    doc.querySelector('[data-tab-panel="more"] [data-nav-tab="secrets"]').click();
    assert.equal(doc.querySelector('[data-tab-panel="secrets"]').hidden,false);
    doc.querySelector('.bottom-nav [data-nav-tab="more"]').click();
    doc.querySelector('[data-tab-panel="more"] [data-nav-tab="couple"]').click();
    assert.equal(doc.querySelector('[data-tab-panel="couple"]').hidden,false);
    assert.deepEqual(errors,[]);
  } finally {dom.window.close();}
});
test('quick starts and mood selector work in one tap',()=>{
  const {dom,doc,errors}=app();
  try {
    for(const kind of ['quick-question','quick-dare']) {
      doc.querySelector(`[data-open-feature="${kind}"]`).click();
      assert.ok(doc.querySelector('#prompt .prompt-main-text').textContent.length>15);
      let before=doc.querySelector('#prompt .prompt-main-text').textContent;
      doc.querySelector('[data-action="quick-next"]').click();
      assert.notEqual(doc.querySelector('#prompt .prompt-main-text').textContent,before);
      doc.querySelector('#sheetCloseBtn').click();
    }
    doc.querySelector('#headerMoodChip').click();
    doc.querySelector('#sheetContent [data-set-mood="fiery"]').click();
    assert.equal(doc.documentElement.dataset.mood,'fiery');
    assert.ok(doc.querySelector('#sheetContent [data-set-mood="fiery"]').classList.contains('is-selected'));
    assert.deepEqual(errors,[]);
  } finally {dom.window.close();}
});
