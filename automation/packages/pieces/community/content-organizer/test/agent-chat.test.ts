/// <reference types="vitest/globals" />

import { agentChat } from '../src/lib/actions/agent-chat';
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

function fakeFetch() {
  const calls: Call[] = [];
  const impl = (async (input: string | URL, init?: RequestInit) => {
    const url = String(input);
    calls.push({ url, init });
    if (url === ENV.IB2B_TOKEN_URL) {
      return new Response(JSON.stringify({ issued_token: 'jwt-1' }), { status: 200 });
    }
    if (url.endsWith('/chat/completion')) {
      return new Response(JSON.stringify({
        code: '00000',
        data: { message: [{ content: 'Tables designed.\n---GENERATED_DATA_START---\n{}\n---GENERATED_DATA_END---\n' }] },
      }), { status: 200 });
    }
    return new Response('not found', { status: 404 });
  }) as typeof fetch;
  return { impl, calls };
}

describe('agentChat', () => {
  test('sends the whole message to the agent setting and returns the reply untouched inside', async () => {
    const { impl, calls } = fakeFetch();
    const message = '## MODE: PROPOSE\n## PHASE: TABLE_DESIGN\n## USER REQUEST\nDesign the tables';

    const out = await agentChat({ message, staffId: ' 45349679 ', fetchImpl: impl, env: ENV });

    expect(out.reply).toBe('Tables designed.\n---GENERATED_DATA_START---\n{}\n---GENERATED_DATA_END---');
    expect(calls.map((c) => c.url)).toEqual([ENV.IB2B_TOKEN_URL, 'https://co.example/proxy/v1/api/management-service/chat/completion']);
    const body = JSON.parse(String(calls[1].init!.body));
    expect(body.sessionId).toBe(out.sessionId);
    expect(body.userId).toBe('45349679');
    expect(body.parameter.messages).toEqual([{ role: 'user', content: message }]);
    expect(body.parameter.message).toBe(message);
    expect(body.parameter.agentSetting).toEqual({ id: 'agent-v1', agentVars: [] });
    expect(body.parameter.promptSetting).toEqual({ id: 'agent-v1', promptVars: [] });
  });

  test('rejects an empty message or staff id before calling anything', async () => {
    const { impl, calls } = fakeFetch();
    await expect(agentChat({ message: ' ', staffId: '1', fetchImpl: impl, env: ENV })).rejects.toThrow(/Message is empty/);
    await expect(agentChat({ message: 'x', staffId: '', fetchImpl: impl, env: ENV })).rejects.toThrow(/Staff ID is empty/);
    expect(calls).toHaveLength(0);
  });
});

describe('readConfig for agent', () => {
  test('requires only the agent setting id, no variable', () => {
    expect(readConfig(ENV, 'agent')).toMatchObject({ promptSettingId: 'agent-v1', promptVariableId: undefined });
    expect(() => readConfig({ ...ENV, CONTENT_ORGANIZER_AGENT_SETTING_ID: '' }, 'agent')).toThrow(/CONTENT_ORGANIZER_AGENT_SETTING_ID\./);
  });
});
