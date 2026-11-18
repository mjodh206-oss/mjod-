import {test} from 'node:test';
import assert from 'node:assert/strict';
import {invitePage} from './operator.mjs';
import {authorize} from './access.mjs';
const secret='this is a fake test secret with high length';
const admin='this is a fake test password with high length';
function render(page,password){
  const out={};page({method:'GET',headers:{host:'hostile.test',authorization:`Basic ${Buffer.from(`operator:${password}`).toString('base64')}`}},{
    writeHead(status,headers){out.status=status;out.headers=headers;return this;},end(body){out.body=body;}});
  return out;
}
test('operator page only issues short-lived links to authenticated operators on the configured public origin',()=>{
  assert.equal(invitePage({origin:'http://operator.example/',secret,admin}),null);
  const page=invitePage({origin:'https://operator.example/',secret,admin});
  assert.equal(render(page,'wrong').status,401);
  const out=render(page,admin);
  assert.equal(out.status,200);assert.equal(out.headers['Cache-Control'],'no-store');
  assert.ok(!out.body.includes('hostile.test'));
  const link=/href="https:\/\/operator.example\/pair#([^"]+)"/.exec(out.body);
  assert.ok(authorize(`Bearer ${link[1]}`,{secret}));
  assert.ok(!authorize(`Bearer ${admin}`,{secret}));
});
