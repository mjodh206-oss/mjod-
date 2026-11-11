/**
 * Majwuda Gateway — translation between the Anthropic Messages API (what مجودة speaks)
 * and OpenAI-compatible chat completions (what free providers speak).
 *
 * Pure functions / one small state machine for SSE translation. No I/O here other than
 * none at all — network lives in index.js so this file is unit-testable offline.
 */

const rand = () => Math.random().toString(36).slice(2, 10) + Date.now().toString(36);
const safeParse = (raw) => {
  if (raw == null || raw === '') return {};
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : { _raw: String(raw) };
  } catch {
    return { _raw: String(raw) };
  }
};

function systemText(sys) {
  if (!sys) return '';
  if (typeof sys === 'string') return sys;
  if (Array.isArray(sys)) {
    return sys.map((b) => (typeof b === 'string' ? b : b?.text || '')).filter(Boolean).join('\n\n');
  }
  return '';
}

function toolResultText(tr) {
  let text;
  const c = tr.content;
  if (typeof c === 'string') text = c;
  else if (Array.isArray(c)) {
    text = c.map((b) => (typeof b === 'string' ? b : b?.type === 'text' ? b.text || '' : `[${b?.type}]`)).join('\n');
  } else if (c != null) text = JSON.stringify(c);
  else text = '';
  if (tr.is_error) text = `Error: ${text}`;
  return text.trim() || '(empty tool result)';
}

/**
 * Anthropic Messages request → OpenAI chat-completion payload.
 * `env.PROVIDER_MODEL` decides which actual model runs (the app asks for claude-* names,
 * which the provider neither knows nor should receive).
 */
export function anthropicToOpenAI(req, env = {}) {
  const out = {
    model: env.PROVIDER_MODEL || 'gemini-2.5-flash',
    max_tokens: Math.min(Math.max(Number(req?.max_tokens) || 1024, 1), 2048),
    messages: [],
  };
  const sys = systemText(req?.system);
  if (sys) out.messages.push({ role: 'system', content: sys });

  for (const m of req?.messages || []) {
    const blocks = Array.isArray(m.content) ? m.content : [{ type: 'text', text: String(m.content ?? '') }];
    if (m.role === 'assistant') {
      const text = blocks.filter((b) => b.type === 'text').map((b) => b.text || '').join('');
      const calls = blocks
        .filter((b) => b.type === 'tool_use')
        .map((b) => ({
          id: b.id || 'toolu_' + rand(),
          type: 'function',
          function: { name: b.name || 'unknown', arguments: JSON.stringify(b.input ?? {}) },
        }));
      const msg = { role: 'assistant', content: text || null };
      if (calls.length) msg.tool_calls = calls;
      out.messages.push(msg);
      continue;
    }
    // user role: tool_result blocks become role='tool' messages; the rest form the user turn
    for (const tr of blocks.filter((b) => b.type === 'tool_result')) {
      out.messages.push({ role: 'tool', tool_call_id: tr.tool_use_id || '', content: toolResultText(tr) });
    }
    const others = blocks.filter((b) => b.type !== 'tool_result');
    const parts = [];
    for (const b of others) {
      if (b.type === 'text') parts.push({ type: 'text', text: b.text || '' });
      else if (b.type === 'image' && b.source?.type === 'base64') {
        parts.push({ type: 'image_url', image_url: { url: `data:${b.source.media_type};base64,${b.source.data}` } });
      }
    }
    if (parts.length === 1 && parts[0].type === 'text') out.messages.push({ role: 'user', content: parts[0].text });
    else if (parts.length) out.messages.push({ role: 'user', content: parts });
  }

  if (Array.isArray(req?.tools) && req.tools.length) {
    out.tools = req.tools.map((t) => ({
      type: 'function',
      function: {
        name: t.name,
        description: t.description || '',
        parameters: t.input_schema && typeof t.input_schema === 'object'
          ? t.input_schema
          : { type: 'object', properties: {} },
      },
    }));
  }
  if (req?.stream) {
    out.stream = true;
    // Ask the provider to include a final usage chunk; if it ignores this, zeros are used.
    out.stream_options = { include_usage: true };
  }
  return out;
}

function finishReason(reason) {
  switch (reason) {
    case 'stop': return 'end_turn';
    case 'tool_calls': return 'tool_use';
    case 'length': return 'max_tokens';
    case 'content_filter': return 'refusal';
    case 'end_turn': return 'end_turn';
    default: return 'end_turn';
  }
}

/** OpenAI non-stream completion → Anthropic message JSON. */
export function openAIToAnthropic(resp, requestedModel) {
  const choice = resp?.choices?.[0] || {};
  const msg = choice.message || {};
  const content = [];
  if (typeof msg.content === 'string' && msg.content) content.push({ type: 'text', text: msg.content });
  for (const tc of msg.tool_calls || []) {
    content.push({
      type: 'tool_use',
      id: tc.id || 'toolu_' + rand(),
      name: tc.function?.name || 'unknown',
      input: safeParse(tc.function?.arguments),
    });
  }
  if (!content.length) content.push({ type: 'text', text: '' });
  const usage = resp?.usage || {};
  return {
    id: 'msg_' + String(resp?.id || rand()).replace(/^chatcmpl-/, '').replace(/[^a-zA-Z0-9_-]/g, ''),
    type: 'message',
    role: 'assistant',
    model: requestedModel || resp?.model || 'gateway-model',
    content,
    stop_reason: finishReason(choice.finish_reason),
    stop_sequence: null,
    usage: {
      input_tokens: usage.prompt_tokens ?? 0,
      output_tokens: usage.completion_tokens ?? 0,
      cache_creation_input_tokens: 0,
      cache_read_input_tokens: 0,
    },
  };
}

const sse = (event, data) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;

/**
 * Translates an OpenAI SSE stream (as parsed chunk objects) into Anthropic SSE frames.
 * Usage: feed every parsed `data:` chunk via [handleOpenAIChunk]; call [finish] at EOF.
 */
export class SseTranslator {
  constructor(requestedModel) {
    this.model = requestedModel || 'gateway-model';
    this.id = 'msg_' + rand();
    this.started = false;
    this.nextBlockIndex = 0;
    this.textBlock = null;          // { blockIndex }
    this.toolBlocks = new Map();    // openAI tool index -> { blockIndex, id, name }
    this.inputTokens = 0;
    this.outputTokens = 0;
    this.reason = 'end_turn';
  }

  frames(frames) { return frames.filter(Boolean).join(''); }

  ensureStart() {
    if (this.started) return '';
    this.started = true;
    return sse('ping', { type: 'ping' }) +
      sse('message_start', {
        type: 'message_start',
        message: {
          id: this.id,
          type: 'message',
          role: 'assistant',
          model: this.model,
          content: [],
          stop_reason: null,
          stop_sequence: null,
          usage: { input_tokens: this.inputTokens, output_tokens: 0 },
        },
      });
  }

  ensureTextBlock() {
    if (this.textBlock) return '';
    this.textBlock = { blockIndex: this.nextBlockIndex++ };
    return sse('content_block_start', {
      type: 'content_block_start',
      index: this.textBlock.blockIndex,
      content_block: { type: 'text', text: '' },
    });
  }

  ensureToolBlock(openAIIndex, id, name) {
    let block = this.toolBlocks.get(openAIIndex);
    if (block) return '';
    block = { blockIndex: this.nextBlockIndex++, id: id || 'toolu_' + rand(), name: name || 'unknown' };
    this.toolBlocks.set(openAIIndex, block);
    return sse('content_block_start', {
      type: 'content_block_start',
      index: block.blockIndex,
      content_block: { type: 'tool_use', id: block.id, name: block.name, input: {} },
    });
  }

  closeBlock(index) {
    return sse('content_block_stop', { type: 'content_block_stop', index });
  }

  /** @returns {string} zero or more SSE frames (possibly empty) */
  handleOpenAIChunk(chunk) {
    let out = this.ensureStart();
    const choice = chunk?.choices?.[0];
    if (chunk?.usage) {
      this.inputTokens = chunk.usage.prompt_tokens ?? this.inputTokens;
      this.outputTokens = chunk.usage.completion_tokens ?? this.outputTokens;
    }
    const delta = choice?.delta;
    if (delta?.content) {
      out += this.ensureTextBlock();
      out += sse('content_block_delta', {
        type: 'content_block_delta',
        index: this.textBlock.blockIndex,
        delta: { type: 'text_delta', text: delta.content },
      });
    }
    for (const tc of delta?.tool_calls || []) {
      const id = tc.id || undefined;
      const name = tc.function?.name || undefined;
      out += this.ensureToolBlock(tc.index ?? 0, id, name);
      const args = tc.function?.arguments || '';
      if (args) {
        const block = this.toolBlocks.get(tc.index ?? 0);
        out += sse('content_block_delta', {
          type: 'content_block_delta',
          index: block.blockIndex,
          delta: { type: 'input_json_delta', partial_json: args },
        });
      }
    }
    if (choice?.finish_reason) this.reason = finishReason(choice.finish_reason);
    return out;
  }

  /** Close everything and emit message_delta + message_stop. */
  finish() {
    let out = this.ensureStart();
    if (this.textBlock) { out += this.closeBlock(this.textBlock.blockIndex); this.textBlock = null; }
    for (const [, block] of [...this.toolBlocks.entries()].sort((a, b) => a[1].blockIndex - b[1].blockIndex)) {
      out += this.closeBlock(block.blockIndex);
    }
    this.toolBlocks.clear();
    out += sse('message_delta', {
      type: 'message_delta',
      delta: { stop_reason: this.reason, stop_sequence: null },
      usage: { output_tokens: this.outputTokens },
    });
    out += sse('message_stop', { type: 'message_stop' });
    return out;
  }
}

export const __internals = { finishReason, safeParse, systemText, toolResultText };
