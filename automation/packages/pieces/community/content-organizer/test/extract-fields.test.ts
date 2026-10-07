/// <reference types="vitest/globals" />

import { extractFields } from '../src/lib/actions/extract-fields';
import { readConfig } from '../src/lib/common/config';

const ENV = {
  IB2B_TOKEN_URL: 'https://ib2b.example/translate',
  IB2B_USERNAME: 'HK-HERMES-D',
  IB2B_SECRET: 's3cret',
  CONTENT_ORGANIZER_BASE_URL: 'https://co.example/proxy/v1/',
  CONTENT_ORGANIZER_APPLICATION_ID: 'app-1',
  CONTENT_ORGANIZER_PROMPT_SETTING_ID: 'ps-1',
  CONTENT_ORGANIZER_PROMPT_VARIABLE_ID: 'var-1',
};
const FILE_URL = 'http://developer-workstation:8080/api/v1/upload/files/a1.pdf?originalName=Receipt+Notice.pdf';
const FIELDS = [{ key: 'receipt_number', label: 'Receipt Number' }];

type Call = { url: string; init?: RequestInit };

/** Fake network: routes by URL; `coStatus` lets a test make Content Organizer reject the first token. */
function fakeFetch(opts: { coRejectFirstToken?: boolean } = {}) {
  const calls: Call[] = [];
  let tokens = 0;
  let rejected = false;
  const impl = (async (input: string | URL, init?: RequestInit) => {
    const url = String(input);
    calls.push({ url, init });
    if (url === ENV.IB2B_TOKEN_URL) {
      tokens += 1;
      return new Response(JSON.stringify({ issued_token: `jwt-${tokens}` }), { status: 200 });
    }
    if (url.startsWith('http://developer-workstation')) {
      return new Response(new Uint8Array([37, 80, 68, 70]), { status: 200, headers: { 'content-type': 'application/pdf' } });
    }
    const token = (init?.headers as Record<string, string>)['X-HSBC-E2E-Trust-Token'];
    if (opts.coRejectFirstToken && !rejected && token === 'jwt-1') {
      rejected = true;
      return new Response('', { status: 401 });
    }
    if (url.endsWith('/files')) {
      return new Response(JSON.stringify({ code: '00000', data: [{ file_id: 'f-1' }] }), { status: 200 });
    }
    if (url.endsWith('/chat/completion')) {
      return new Response(JSON.stringify({
        code: '00000',
        data: { message: [{ content: '{"receipt_number":"MCT2373613541"}' }] },
      }), { status: 200 });
    }
    return new Response('not found', { status: 404 });
  }) as typeof fetch;
  return { impl, calls };
}

describe('extractFields', () => {
  test('uploads then completes in one session with the staff id, and parses the answer', async () => {
    const { impl, calls } = fakeFetch();

    const out = await extractFields({ fileUrl: FILE_URL, staffId: '45349679', fields: FIELDS, fetchImpl: impl, env: ENV });

    expect(out.values).toEqual({ receipt_number: 'MCT2373613541' });
    expect(out.fileId).toBe('f-1');
    expect(calls.filter((c) => c.url === ENV.IB2B_TOKEN_URL)).toHaveLength(1); // one token per run

    const ib2b = calls.find((c) => c.url === ENV.IB2B_TOKEN_URL)!;
    expect(JSON.parse(String(ib2b.init!.body))).toEqual({
      input_token_state: { token_type: 'CREDENTIAL', username: 'HK-HERMES-D', password: 's3cret' },
      output_token_state: { token_type: 'JWT' },
    });

    const upload = calls.find((c) => c.url.endsWith('/files'))!;
    expect(upload.url).toBe(`https://co.example/proxy/v1/api/management-service/api/applications/app-1/sessions/${out.sessionId}/files`);
    const form = upload.init!.body as FormData;
    expect(form.get('userId')).toBe('45349679');
    expect((form.get('files') as File).name).toBe('Receipt Notice.pdf');

    const completion = calls.find((c) => c.url.endsWith('/chat/completion'))!;
    const body = JSON.parse(String(completion.init!.body));
    expect(body.sessionId).toBe(out.sessionId);
    expect(body.userId).toBe('45349679');
    expect(body.parameter.applicationId).toBe('app-1');
    expect(body.parameter).toMatchObject({
      applicationName: 'Hermes Workflow',
      model: 'gemini-3.5-flash',
      llmProvider: 'OpenAI',
      apiVersion: '2023-03-15-preview',
      promptEngineer: 'true',
      enableQuestionDetection: 'false',
      referDocumentList: [],
      referDocumentbaseList: [],
    });
    expect(body.parameter.messages).toEqual([{ role: 'user', content: 'Extract the fields from the uploaded document.' }]);
    expect(body.parameter.promptSetting).toEqual({
      id: 'ps-1',
      promptVars: [{ name: 'var-1', value: ['- receipt_number: "Receipt Number" (text)'] }],
      settingType: 'PROMPT',
    });
    expect(body.workflow).toBe('default');
    expect(body.version).toBe('1.0');
    expect(body.defaultOptions).toEqual({ language: 'English', noOfOutput: 1 });
  });

  test('a 401 from Content Organizer mints a new iB2B token and retries once', async () => {
    const { impl, calls } = fakeFetch({ coRejectFirstToken: true });

    const out = await extractFields({ fileUrl: FILE_URL, staffId: '45349679', fields: FIELDS, fetchImpl: impl, env: ENV });

    expect(out.values.receipt_number).toBe('MCT2373613541');
    const tokenOf = (c: Call) => (c.init!.headers as Record<string, string>)['X-HSBC-E2E-Trust-Token'];
    expect(calls.filter((c) => c.url.endsWith('/files')).map(tokenOf)).toEqual(['jwt-1', 'jwt-2']);
    // the refreshed token is reused for the rest of the run
    expect(calls.filter((c) => c.url.endsWith('/chat/completion')).map(tokenOf)).toEqual(['jwt-2']);
    expect(calls.filter((c) => c.url === ENV.IB2B_TOKEN_URL)).toHaveLength(2);
  });

  test('a 422 validation error keeps its detail readable', async () => {
    const { impl } = fakeFetch();
    const failing = (async (input: string | URL, init?: RequestInit) => String(input).endsWith('/chat/completion')
      ? new Response(JSON.stringify({ detail: [{ type: 'missing', loc: ['body', 'parameter', 'model'], msg: 'Field required' }] }), { status: 422 })
      : impl(input, init)) as typeof fetch;

    await expect(extractFields({ fileUrl: FILE_URL, staffId: '45349679', fields: FIELDS, fetchImpl: failing, env: ENV }))
      .rejects.toThrow('Chat completion failed (HTTP 422): [{"type":"missing","loc":["body","parameter","model"],"msg":"Field required"}]');
  });

  test('rejects a multi-file value before calling anything', async () => {
    const { impl, calls } = fakeFetch();
    await expect(extractFields({
      fileUrl: '[{"url":"/api/v1/upload/files/a.pdf"}]', staffId: '1', fields: FIELDS, fetchImpl: impl, env: ENV,
    })).rejects.toThrow(/Exactly one file/);
    expect(calls).toHaveLength(0);
  });
});

describe('readConfig', () => {
  test('names every missing variable', () => {
    expect(() => readConfig({ IB2B_TOKEN_URL: 'x' })).toThrow(
      /IB2B_USERNAME, IB2B_SECRET, CONTENT_ORGANIZER_BASE_URL, CONTENT_ORGANIZER_APPLICATION_ID, CONTENT_ORGANIZER_PROMPT_SETTING_ID, CONTENT_ORGANIZER_PROMPT_VARIABLE_ID/,
    );
  });

  test('strips the trailing slash of the base url and defaults the api version and workflow', () => {
    const config = readConfig(ENV);
    expect(config.baseUrl).toBe('https://co.example/proxy/v1');
    expect(config.apiVersion).toBe('2024-10-01-preview');
    expect(config.workflow).toBe('default');
    expect(config.workflowVersion).toBe('1.0');
    expect(config.model).toBe('gemini-3.5-flash');
    expect(config.applicationName).toBe('Hermes Workflow');
    expect(readConfig({ ...ENV, CONTENT_ORGANIZER_WORKFLOW: 'ReasearchChatCompletion', CONTENT_ORGANIZER_WORKFLOW_VERSION: '001' }))
      .toMatchObject({ workflow: 'ReasearchChatCompletion', workflowVersion: '001' });
  });
});
