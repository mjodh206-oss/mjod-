import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.js';

const BASE_ENV = {
  PROVIDER_BASE_URL: 'https://provider.test/v1',
  PROVIDER_MODEL: 'gemini-2.5-flash',
  PROVIDER_API_KEY: 'provider-key',
  APP_TOKEN: 'shared-secret',
};

function postMessages(body, headers = {}) {
  return new Request('https://majwuda-gateway.test/v1/messages', {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-api-key': 'shared-secret', ...headers },
    body: JSON.stringify(body),
  });
}

const okCompletion = {
  id: 'chatcmpl-ok',
  model: 'gemini-2.5-flash',
  choices: [{ message: { role: 'assistant', content: 'الجواب: 4' }, finish_reason: 'stop' }],
  usage: { prompt_tokens: 30, completion_tokens: 5 },
};

function withFetchStub(handler, fn) {
  const original = globalThis.fetch;
  globalThis.fetch = handler;
  return Promise.resolve()
    .then(fn)
    .finally(() => { globalThis.fetch = original; });
}

test('GET /health returns ok without any secret', async () => {
  const res = await worker.fetch(new Request('https://majwuda-gateway.test/health'), {});
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.ok, true);
});

test('unknown paths return Anthropic-shaped 404', async () => {
  const res = await worker.fetch(new Request('https://majwuda-gateway.test/v1/models'), BASE_ENV);
  assert.equal(res.status, 404);
  const body = await res.json();
  assert.equal(body.type, 'error');
});

test('missing or wrong APP_TOKEN returns 401 before calling the provider', async () => {
  let called = false;
  await withFetchStub(async () => { called = true; return new Response('{}'); }, async () => {
    const res = await worker.fetch(postMessages({ max_tokens: 5, messages: [] }, { 'x-api-key': 'wrong' }), BASE_ENV);
    assert.equal(res.status, 401);
    const resNoHeader = await worker.fetch(new Request('https://majwuda-gateway.test/v1/messages', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ max_tokens: 5, messages: [] }),
    }), BASE_ENV);
    assert.equal(resNoHeader.status, 401);
  });
  assert.equal(called, false);
});

test('non-stream request proxies to the provider and returns Anthropic JSON', async () => {
  let providerUrl = '';
  let providerAuth = '';
  let providerBody = null;
  await withFetchStub(async (url, opts) => {
    providerUrl = url;
    providerAuth = opts.headers.authorization;
    providerBody = JSON.parse(opts.body);
    return new Response(JSON.stringify(okCompletion), { status: 200, headers: { 'content-type': 'application/json' } });
  }, async () => {
    const res = await worker.fetch(postMessages({
      model: 'claude-sonnet-4-5', max_tokens: 100,
      system: [{ type: 'text', text: 'أنت مجودة' }],
      messages: [{ role: 'user', content: [{ type: 'text', text: 'احسب 2+2' }] }],
    }), BASE_ENV);
    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.type, 'message');
    assert.equal(body.content[0].text, 'الجواب: 4');
    assert.equal(body.stop_reason, 'end_turn');
    assert.equal(body.usage.input_tokens, 30);
  });
  assert.equal(providerUrl, 'https://provider.test/v1/chat/completions');
  assert.equal(providerAuth, 'Bearer provider-key');
  assert.equal(providerBody.model, 'gemini-2.5-flash'); // claude-* never reaches the provider
  assert.equal(providerBody.messages[0].role, 'system');
  assert.equal(providerBody.messages.at(-1).content, 'احسب 2+2');
});

test('provider rate limit maps to Anthropic 429 rate_limit_error', async () => {
  await withFetchStub(async () => new Response(JSON.stringify({ error: { message: 'quota exceeded' } }), { status: 429 }), async () => {
    const res = await worker.fetch(postMessages({ max_tokens: 5, messages: [{ role: 'user', content: 'hi' }] }), BASE_ENV);
    assert.equal(res.status, 429);
    const body = await res.json();
    assert.equal(body.error.type, 'rate_limit_error');
    assert.match(body.error.message, /quota/i);
  });
});

test('stream request returns an Anthropic SSE stream with the full event sequence', async () => {
  const providerSse = [
    'data: {"id":"c1","choices":[{"index":0,"delta":{"role":"assistant","content":"مرح"},"finish_reason":null}]}',
    'data: {"id":"c1","choices":[{"index":0,"delta":{"content":"بًا"},"finish_reason":null}]}',
    'data: {"id":"c1","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}',
    'data: {"id":"c1","choices":[],"usage":{"prompt_tokens":11,"completion_tokens":4}}',
    'data: [DONE]',
    '',
  ].join('\n');
  await withFetchStub(async () => new Response(providerSse, { status: 200, headers: { 'content-type': 'text/event-stream' } }), async () => {
    const res = await worker.fetch(postMessages({
      model: 'claude-sonnet-4-5', max_tokens: 100, stream: true,
      messages: [{ role: 'user', content: [{ type: 'text', text: 'سلم علي' }] }],
    }), BASE_ENV);
    assert.equal(res.status, 200);
    assert.match(res.headers.get('content-type'), /text\/event-stream/);
    const text = await res.text();
    assert.match(text, /event: message_start/);
    assert.match(text, /"text_delta","text":"مرح"/);
    assert.match(text, /"text_delta","text":"بًا"/);
    assert.match(text, /"stop_reason":"end_turn"/);
    assert.match(text, /"output_tokens":4/);
    assert.match(text, /event: message_stop/);
  });
});

test('stream request with tool calls yields tool_use blocks and tool_use stop reason', async () => {
  const providerSse = [
    'data: {"id":"c2","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"calculator","arguments":""}}]},"finish_reason":null}]}',
    'data: {"id":"c2","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\\"expression\\":"}}]},"finish_reason":null}]}',
    'data: {"id":"c2","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\\"2+2\\"}"}}]},"finish_reason":null}]}',
    'data: {"id":"c2","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}',
    'data: [DONE]',
    '',
  ].join('\n');
  await withFetchStub(async () => new Response(providerSse, { status: 200 }), async () => {
    const res = await worker.fetch(postMessages({
      model: 'claude-sonnet-4-5', max_tokens: 100, stream: true,
      messages: [{ role: 'user', content: [{ type: 'text', text: 'احسب' }] }],
    }), BASE_ENV);
    const text = await res.text();
    assert.match(text, /"name":"calculator"/);
    assert.match(text, /input_json_delta/);
    assert.match(text, /"stop_reason":"tool_use"/);
    assert.match(text, /event: message_stop/);
  });
});
