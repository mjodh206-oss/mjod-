import {test} from 'node:test';
import assert from 'node:assert/strict';
import {authorize,issue} from './access.mjs';
const secret='test with a sufficiently long provisioning secret';
const now=1_800_000_000;
test('activation tokens are scoped, expiring, and authenticated',()=>{
  const a=issue(secret,3600,now);
  assert.notEqual(a,issue(secret,3600,now));
  assert.ok(authorize(`Bearer ${a}`,{secret},now));
  assert.ok(!authorize(`Bearer ${a}`,{secret},now+3600));
  assert.ok(!authorize(`Bearer ${a.replace(`.${now+3600}.`,`.${now+7200}.`)}`,{secret},now));
  assert.ok(!authorize(`Bearer ${a}`,{secret:secret+' other'},now));
  assert.ok(!authorize(a,{secret},now));
});
