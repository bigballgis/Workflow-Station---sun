/// <reference types="vitest/globals" />

import { agentChat } from '../src/lib/actions/agent-chat';
import { agentAnswer } from '../src/lib/common/client';
import { readConfig } from '../src/lib/common/config';

const ENV = {
  IB2B_TOKEN_URL: 'https://ib2b.example/translate',
  IB2B_USERNAME: 'HK-HERMES-D',
  IB2B_SECRET: 's3cret',
  CONTENT_ORGANIZER_BASE_URL: 'https://co.example/proxy/v1',
  CONTENT_ORGANIZER_APPLICATION_ID: 'app-1',
  CONTENT_ORGANIZER_AGENT_SETTING_ID: 'agent-v1',
};

type Call = { url: string; init?: RequestInit };

const sse = (...events: object[]) => events.map((e) => `data: ${JSON.stringify(e)}\n\n`).join('');

function fakeFetch() {
  const calls: Call[] = [];
  const impl = (async (input: string | URL, init?: RequestInit) => {
    const url = String(input);
    calls.push({ url, init });
    if (url === ENV.IB2B_TOKEN_URL) {
      return new Response(JSON.stringify({ issued_token: 'jwt-1' }), { status: 200 });
    }
    if (url.endsWith('/agents/run-sse')) {
      // the event shapes the Content Organizer Test Workbench receives (2026-11-03)
      return new Response(sse(
        { type: 'status', content: '[thinking]**Analyzing**' },
        { type: 'chunk', content: 'Tables designed.', author: 'table_design_agent' },
        { type: 'full', content: 'Tables designed.\n---GENERATED_DATA_START---\n{}\n---GENERATED_DATA_END---\n' },
        { type: 'done', status: 'success', usage: { executionTime: 3.7 } },
      ), { status: 200, headers: { 'Content-Type': 'text/event-stream' } });
    }
    return new Response('not found', { status: 404 });
  }) as typeof fetch;
  return { impl, calls };
}

describe('agentChat', () => {
  test('runs the agent setting on agents/run-sse with the documented body and returns the answer', async () => {
    const { impl, calls } = fakeFetch();
    const message = '## MODE: PROPOSE\n## PHASE: TABLE_DESIGN\n## USER REQUEST\nDesign the tables';

    const out = await agentChat({ message, staffId: ' 45349679 ', fetchImpl: impl, env: ENV });

    expect(out.reply).toBe('Tables designed.\n---GENERATED_DATA_START---\n{}\n---GENERATED_DATA_END---');
    expect(calls.map((c) => c.url)).toEqual([ENV.IB2B_TOKEN_URL, 'https://co.example/proxy/v1/api/management-service/agents/run-sse']);
    const headers = calls[1].init!.headers as Record<string, string>;
    expect(headers['Accept']).toBe('text/event-stream');
    expect(headers['X-HSBC-E2E-Trust-Token']).toBe('jwt-1');
    // CO API Request Examples #12: parameter.{applicationId, message, agentSetting} + user_id (iB2B only)
    expect(JSON.parse(String(calls[1].init!.body))).toEqual({
      sessionId: out.sessionId,
      parameter: { applicationId: 'app-1', message, agentSetting: { id: 'agent-v1', agentVars: [] } },
      user_id: '45349679',
    });
  });

  test('rejects an empty message or staff id before calling anything', async () => {
    const { impl, calls } = fakeFetch();
    await expect(agentChat({ message: ' ', staffId: '1', fetchImpl: impl, env: ENV })).rejects.toThrow(/Message is empty/);
    await expect(agentChat({ message: 'x', staffId: '', fetchImpl: impl, env: ENV })).rejects.toThrow(/Staff ID is empty/);
    expect(calls).toHaveLength(0);
  });
});

describe('agentAnswer', () => {
  test('joins every full segment in order and ignores status and chunk events', () => {
    expect(agentAnswer(sse(
      { type: 'status', content: '[analyzing]**Analyzing**' },
      { type: 'chunk', content: 'Routing', author: 'dw_function_unit_orchestrator' },
      { type: 'full', content: 'Routing to the table agent.', author: 'dw_function_unit_orchestrator' },
      { type: 'chunk', content: 'Tables', author: 'table_design_agent' },
      { type: 'full', content: 'Tables designed.', author: 'table_design_agent' },
      { type: 'done', status: 'success' },
    ))).toBe('Routing to the table agent.\n\nTables designed.');
  });

  test('fails on an error event, an unsuccessful or missing done, a missing answer or an unknown type', () => {
    expect(() => agentAnswer(sse({ type: 'full', content: 'x' },
      { type: 'error', code: 'FORBIDDEN_CONTENT', message: 'blocked' }, { type: 'done', status: 'failed' })))
      .toThrow(/FORBIDDEN_CONTENT\): blocked/);
    expect(() => agentAnswer(sse({ type: 'full', content: 'x' }, { type: 'done', status: 'failed' }))).toThrow(/did not succeed/);
    expect(() => agentAnswer(sse({ type: 'full', content: 'x' }))).toThrow(/without a done event/);
    // partial chunks are never stitched into an answer
    expect(() => agentAnswer(sse({ type: 'chunk', content: 'x' }, { type: 'done', status: 'success' }))).toThrow(/no answer/);
    expect(() => agentAnswer(sse({ type: 'tool_call', content: 'x' }, { type: 'done', status: 'success' }))).toThrow(/unknown event type/);
  });
});

describe('readConfig for agent', () => {
  test('requires only the agent setting id, no variable', () => {
    expect(readConfig(ENV, 'agent')).toMatchObject({ promptSettingId: 'agent-v1', promptVariableId: undefined });
    expect(() => readConfig({ ...ENV, CONTENT_ORGANIZER_AGENT_SETTING_ID: '' }, 'agent')).toThrow(/CONTENT_ORGANIZER_AGENT_SETTING_ID\./);
  });
});
