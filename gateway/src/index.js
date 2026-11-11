/**
 * Majwuda Gateway — Cloudflare Worker.
 *
 * Speaks the Anthropic Messages API to مجودة and proxies to any OpenAI-compatible
 * provider (Google AI Studio free tier by default). Set PROVIDER_API_KEY (and optionally
 * APP_TOKEN) with `wrangler secret put`. Zero dependencies.
 */

import { anthropicToOpenAI, openAIToAnthropic, SseTranslator } from './lib.js';

const encoder = new TextEncoder();
const decoder = new TextDecoder();

const json = (obj, status = 200, headers = {}) =>
  new Response(JSON.stringify(obj), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8' },
    ...headers,
  });

const anthropicError = (status, type, message) =>
  json({ type: 'error', error: { type, message } }, status);

function mapStatus(status) {
  if (status === 401 || status === 403) return 401;
  if (status === 429) return 429;
  if (status >= 500) return 502;
  return 400;
}

/** Extract a short, safe message from a provider error body. */
function providerMessage(text) {
  try {
    const body = JSON.parse(text);
    return body?.error?.message || body?.error || body?.message || String(text).slice(0, 300);
  } catch {
    return String(text).slice(0, 300);
  }
}

function streamAnthropic(providerRes, req) {
  const translator = new SseTranslator(req?.model);
  const stream = new ReadableStream({
    async start(controller) {
      const emit = (frame) => { if (frame) controller.enqueue(encoder.encode(frame)); };
      const reader = providerRes.body.getReader();
      let buffer = '';
      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          let newline;
          while ((newline = buffer.indexOf('\n')) >= 0) {
            const line = buffer.slice(0, newline).trim();
            buffer = buffer.slice(newline + 1);
            if (!line || line.startsWith(':')) continue;
            if (!line.startsWith('data:')) continue;
            const data = line.slice(5).trim();
            if (!data || data === '[DONE]') continue;
            let chunk;
            try { chunk = JSON.parse(data); } catch { continue; }
            try {
              emit(translator.handleOpenAIChunk(chunk));
            } catch (err) {
              emit(`event: error\ndata: ${JSON.stringify({ type: 'error', error: { type: 'api_error', message: String(err?.message || err) } })}\n\n`);
            }
          }
        }
        emit(translator.finish());
      } catch (err) {
        emit(`event: error\ndata: ${JSON.stringify({ type: 'error', error: { type: 'api_error', message: String(err?.message || err) } })}\n\n`);
      } finally {
        try { controller.close(); } catch { /* already closed */ }
      }
    },
  });
  return new Response(stream, {
    status: 200,
    headers: {
      'content-type': 'text/event-stream; charset=utf-8',
      'cache-control': 'no-cache',
      'connection': 'keep-alive',
    },
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') return new Response(null, { status: 204 });

    if (request.method === 'GET' && (url.pathname === '/' || url.pathname === '/health')) {
      return json({ ok: true, service: 'majwuda-gateway', model: env.PROVIDER_MODEL || 'gemini-2.5-flash' });
    }

    if (request.method !== 'POST' || url.pathname !== '/v1/messages') {
      return anthropicError(404, 'not_found_error', 'Only POST /v1/messages is supported.');
    }

    // Shared-secret check (recommended): the app sends it as x-api-key.
    if (env.APP_TOKEN && request.headers.get('x-api-key') !== env.APP_TOKEN) {
      return anthropicError(401, 'authentication_error', 'Missing or invalid API key.');
    }

    let req;
    try {
      req = await request.json();
    } catch {
      return anthropicError(400, 'invalid_request_error', 'Request body must be valid JSON.');
    }

    const providerBase = String(env.PROVIDER_BASE_URL || 'https://generativelanguage.googleapis.com/v1beta/openai').replace(/\/+$/, '');
    const providerKey = env.PROVIDER_API_KEY;
    if (!providerKey) {
      return anthropicError(500, 'api_error', 'Gateway is missing PROVIDER_API_KEY (wrangler secret put PROVIDER_API_KEY).');
    }

    const providerBody = anthropicToOpenAI(req, env);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort('timeout'), 110_000);

    let providerRes;
    try {
      providerRes = await fetch(`${providerBase}/chat/completions`, {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          authorization: `Bearer ${providerKey}`,
        },
        body: JSON.stringify(providerBody),
        signal: controller.signal,
      });
    } catch (err) {
      clearTimeout(timer);
      const aborted = String(err?.name || '').includes('Abort') || String(err?.message || '').includes('timeout');
      return anthropicError(aborted ? 504 : 502, 'api_error',
        aborted ? 'النموذج لم يستجب في الوقت المحدد.' : `Failed to reach model provider: ${err?.message || err}`);
    }
    clearTimeout(timer);

    if (!providerRes.ok) {
      const text = await providerRes.text();
      const status = providerRes.status;
      const type = status === 429 ? 'rate_limit_error' : status >= 500 ? 'api_error' : 'invalid_request_error';
      return anthropicError(mapStatus(status), type, providerMessage(text));
    }

    if (req.stream) return streamAnthropic(providerRes, req);

    let data;
    try {
      data = await providerRes.json();
    } catch {
      return anthropicError(502, 'api_error', 'Model provider returned invalid JSON.');
    }
    return json(openAIToAnthropic(data, req?.model));
  },
};
