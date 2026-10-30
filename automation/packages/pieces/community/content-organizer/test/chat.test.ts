/// <reference types="vitest/globals" />

import { chat } from '../src/lib/actions/chat';
import { readConfig } from '../src/lib/common/config';

const ENV = {
  IB2B_TOKEN_URL: 'https://ib2b.example/translate',
  IB2B_USERNAME: 'HK-HERMES-D',
  IB2B_SECRET: 's3cret',
  CONTENT_ORGANIZER_BASE_URL: 'https://co.example/proxy/v1',
  CONTENT_ORGANIZER_APPLICATION_ID: 'app-1',
  CONTENT_ORGANIZER_CHAT_PROMPT_SETTING_ID: 'chat-ps',
  CONTENT_ORGANIZER_CHAT_PROMPT_VARIABLE_ID: 'chat-var',
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
        data: { message: [{ content: '\n1. Open **Function Units**.\n' }] },
      }), { status: 200 });
    }
    return new Response('not found', { status: 404 });
  }) as typeof fetch;
  return { impl, calls };
}

describe('chat', () => {
  test('completes with the chat prompt setting, without uploading anything', async () => {
    const { impl, calls } = fakeFetch();

    const out = await chat({
      message: 'How do I create a Function Unit?',
      context: 'DW page: FunctionUnits\nUser: How do I create a Function Unit?',
      staffId: ' 45349679 ',
      fetchImpl: impl,
      env: ENV,
    });

    expect(out.reply).toBe('1. Open **Function Units**.');
    expect(calls.map((c) => c.url)).toEqual([ENV.IB2B_TOKEN_URL, 'https://co.example/proxy/v1/api/management-service/chat/completion']);
    const body = JSON.parse(String(calls[1].init!.body));
    expect(body.sessionId).toBe(out.sessionId);
    expect(body.userId).toBe('45349679');
    expect(body.parameter.messages).toEqual([{ role: 'user', content: 'How do I create a Function Unit?' }]);
    expect(body.parameter.promptSetting).toEqual({
      id: 'chat-ps',
      promptVars: [{ name: 'chat-var', value: ['DW page: FunctionUnits\nUser: How do I create a Function Unit?'] }],
      settingType: 'PROMPT',
    });
  });

  test('rejects an empty context or staff id before calling anything', async () => {
    const { impl, calls } = fakeFetch();
    await expect(chat({ message: 'hi', context: ' ', staffId: '1', fetchImpl: impl, env: ENV })).rejects.toThrow(/Context is empty/);
    await expect(chat({ message: 'hi', context: 'x', staffId: '', fetchImpl: impl, env: ENV })).rejects.toThrow(/Staff ID is empty/);
    expect(calls).toHaveLength(0);
  });
});

describe('readConfig for chat', () => {
  test('requires the chat prompt setting, not the extraction one', () => {
    expect(readConfig(ENV, 'chat')).toMatchObject({ promptSettingId: 'chat-ps', promptVariableId: 'chat-var' });
    expect(() => readConfig(ENV)).toThrow(/CONTENT_ORGANIZER_PROMPT_SETTING_ID, CONTENT_ORGANIZER_PROMPT_VARIABLE_ID/);
  });
});
