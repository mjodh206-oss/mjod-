import test from 'node:test';
import assert from 'node:assert/strict';
import { anthropicToOpenAI, openAIToAnthropic, SseTranslator, __internals } from '../src/lib.js';

const env = { PROVIDER_MODEL: 'gemini-2.5-flash' };

test('anthropicToOpenAI: system array joins with blank lines, model comes from env', () => {
  const out = anthropicToOpenAI({
    model: 'claude-sonnet-4-5',
    max_tokens: 1800,
    system: [
      { type: 'text', text: 'أنت مجودة.', cache_control: { type: 'ephemeral' } },
      { type: 'text', text: 'اليوم الثلاثاء.' },
    ],
    messages: [{ role: 'user', content: [{ type: 'text', text: 'مرحبا' }] }],
  }, env);
  assert.equal(out.model, 'gemini-2.5-flash');
  assert.equal(out.messages[0].role, 'system');
  assert.equal(out.messages[0].content, 'أنت مجودة.\n\nاليوم الثلاثاء.');
  assert.deepEqual(out.messages[1], { role: 'user', content: 'مرحبا' });
});

test('anthropicToOpenAI: clamps max_tokens to 2048', () => {
  const out = anthropicToOpenAI({ model: 'claude-sonnet-4-5', max_tokens: 99999, messages: [] }, env);
  assert.equal(out.max_tokens, 2048);
});

test('anthropicToOpenAI: image blocks become data URLs', () => {
  const out = anthropicToOpenAI({
    model: 'm', max_tokens: 10,
    messages: [{ role: 'user', content: [
      { type: 'text', text: 'ما هذه الصورة؟' },
      { type: 'image', source: { type: 'base64', media_type: 'image/jpeg', data: 'AAAA' } },
    ] }],
  }, env);
  const content = out.messages[0].content;
  assert.equal(content[0].text, 'ما هذه الصورة؟');
  assert.equal(content[1].type, 'image_url');
  assert.equal(content[1].image_url.url, 'data:image/jpeg;base64,AAAA');
});

test('anthropicToOpenAI: assistant tool_use round-trips into OpenAI tool_calls', () => {
  const out = anthropicToOpenAI({
    model: 'm', max_tokens: 10,
    messages: [
      { role: 'user', content: [{ type: 'text', text: 'احسب' }] },
      { role: 'assistant', content: [{ type: 'tool_use', id: 't1', name: 'calculator', input: { expression: '2+2' } }] },
      { role: 'user', content: [{ type: 'tool_result', tool_use_id: 't1', content: '4' }] },
    ],
  }, env);
  const assistant = out.messages.find((m) => m.role === 'assistant');
  assert.deepEqual(assistant.tool_calls, [{
    id: 't1', type: 'function',
    function: { name: 'calculator', arguments: '{"expression":"2+2"}' },
  }]);
  const tool = out.messages.find((m) => m.role === 'tool');
  assert.deepEqual(tool, { role: 'tool', tool_call_id: 't1', content: '4' });
});

test('anthropicToOpenAI: tool_result array content is flattened to text', () => {
  const out = anthropicToOpenAI({
    model: 'm', max_tokens: 10,
    messages: [{ role: 'user', content: [
      { type: 'tool_result', tool_use_id: 't9', is_error: true, content: [
        { type: 'text', text: 'نفذ الأمر' },
        { type: 'text', text: 'الصلاحية مرفوضة' },
      ] },
    ] }],
  }, env);
  assert.match(out.messages[0].content, /Error: نفذ الأمر/);
  assert.match(out.messages[0].content, /الصلاحية مرفوضة/);
});

test('anthropicToOpenAI: tools map to OpenAI function format', () => {
  const out = anthropicToOpenAI({
    model: 'm', max_tokens: 10, messages: [],
    tools: [{ name: 'open_app', description: 'يفتح تطبيقًا', input_schema: { type: 'object', properties: { app: { type: 'string' } }, required: ['app'] } }],
  }, env);
  assert.deepEqual(out.tools, [{
    type: 'function',
    function: { name: 'open_app', description: 'يفتح تطبيقًا', parameters: { type: 'object', properties: { app: { type: 'string' } }, required: ['app'] } },
  }]);
});

test('openAIToAnthropic: maps text, tool_calls, usage, finish reason', () => {
  const out = openAIToAnthropic({
    id: 'chatcmpl-xyz',
    model: 'gemini-2.5-flash',
    choices: [{ message: { role: 'assistant', content: 'الجواب 4', tool_calls: [
      { id: 'call_1', type: 'function', function: { name: 'calculator', arguments: '{"expression":"2+2"}' } },
    ] }, finish_reason: 'tool_calls' }],
    usage: { prompt_tokens: 100, completion_tokens: 20 },
  }, 'claude-sonnet-4-5');
  assert.equal(out.type, 'message');
  assert.equal(out.model, 'claude-sonnet-4-5'); // echo requested model for the app
  assert.equal(out.stop_reason, 'tool_use');
  assert.deepEqual(out.content, [
    { type: 'text', text: 'الجواب 4' },
    { type: 'tool_use', id: 'call_1', name: 'calculator', input: { expression: '2+2' } },
  ]);
  assert.equal(out.usage.input_tokens, 100);
  assert.equal(out.usage.output_tokens, 20);
});

test('openAIToAnthropic: bad tool arguments fall back to _raw instead of crashing', () => {
  const out = openAIToAnthropic({
    id: 'x', choices: [{ message: { role: 'assistant', content: null, tool_calls: [
      { id: 'c', type: 'function', function: { name: 't', arguments: '{not json' } },
    ] }, finish_reason: 'tool_calls' }],
  }, 'm');
  assert.equal(out.content[0].input._raw, '{not json');
});

test('finishReason mapping', () => {
  const f = __internals.finishReason;
  assert.equal(f('stop'), 'end_turn');
  assert.equal(f('tool_calls'), 'tool_use');
  assert.equal(f('length'), 'max_tokens');
  assert.equal(f('content_filter'), 'refusal');
  assert.equal(f('whatever'), 'end_turn');
});

test('SseTranslator: text stream emits the full anthropic event sequence', () => {
  const t = new SseTranslator('claude-sonnet-4-5');
  let raw = '';
  raw += t.handleOpenAIChunk({ id: 'chatcmpl-1', choices: [{ index: 0, delta: { role: 'assistant', content: 'أهلاً' }, finish_reason: null }] });
  raw += t.handleOpenAIChunk({ id: 'chatcmpl-1', choices: [{ index: 0, delta: { content: ' بك' }, finish_reason: null }] });
  raw += t.handleOpenAIChunk({ id: 'chatcmpl-1', choices: [{ index: 0, delta: {}, finish_reason: 'stop' }], usage: { prompt_tokens: 7, completion_tokens: 3 } });
  raw += t.finish();
  assert.match(raw, /event: message_start\ndata: /);
  assert.match(raw, /"type":"text_delta","text":"أهلاً"/);
  assert.match(raw, /"type":"text_delta","text":" بك"/);
  assert.match(raw, /event: content_block_stop/);
  assert.match(raw, /"stop_reason":"end_turn"/);
  assert.match(raw, /"output_tokens":3/);
  assert.match(raw, /event: message_stop/);
  const order = ['message_start', 'content_block_start', 'content_block_delta', 'content_block_stop', 'message_delta', 'message_stop']
    .map((e) => raw.indexOf(`event: ${e}`));
  assert.deepEqual(order, [...order].sort((a, b) => a - b));
});

test('SseTranslator: tool-call stream opens a tool_use block and passes arguments through', () => {
  const t = new SseTranslator('claude-sonnet-4-5');
  let raw = '';
  raw += t.handleOpenAIChunk({ id: 'c1', choices: [{ index: 0, delta: { role: 'assistant', tool_calls: [{ index: 0, id: 'call_42', type: 'function', function: { name: 'calculator', arguments: '' } }] }, finish_reason: null }] });
  raw += t.handleOpenAIChunk({ id: 'c1', choices: [{ index: 0, delta: { tool_calls: [{ index: 0, function: { arguments: '{"expression":' } }] }, finish_reason: null }] });
  raw += t.handleOpenAIChunk({ id: 'c1', choices: [{ index: 0, delta: { tool_calls: [{ index: 0, function: { arguments: '"6*7"}' } }] }, finish_reason: null }] });
  raw += t.handleOpenAIChunk({ id: 'c1', choices: [{ index: 0, delta: {}, finish_reason: 'tool_calls' }] });
  raw += t.finish();
  assert.match(raw, /"type":"tool_use","id":"call_42","name":"calculator"/);
  const partials = [];
  for (const line of raw.split('\n')) {
    if (!line.startsWith('data: ')) continue;
    try {
      const data = JSON.parse(line.slice(6));
      if (data?.delta?.type === 'input_json_delta') partials.push(data.delta.partial_json);
    } catch { /* ping/message_* frames */ }
  }
  assert.equal(partials.join(''), '{"expression":"6*7"}');
  assert.match(raw, /"stop_reason":"tool_use"/);
});

test('SseTranslator: usage chunk is captured even if it arrives after finish_reason', () => {
  const t = new SseTranslator('m');
  let raw = '';
  raw += t.handleOpenAIChunk({ choices: [{ index: 0, delta: { content: 'hi' }, finish_reason: 'stop' }] });
  raw += t.handleOpenAIChunk({ choices: [], usage: { prompt_tokens: 50, completion_tokens: 9 } });
  raw += t.finish();
  assert.match(raw, /"output_tokens":9/);
});
