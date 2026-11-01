import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { DwApiError, DwClient } from '../src/dwClient';
import { ApplyInput, asToolText, DwTools } from '../src/tools';
import { fakeFetch, json, ORIGIN, signedInAuth } from './fakes';

const input: ApplyInput = { functionUnitId: 388, scope: 'TABLES', generatedData: { tableDefinitions: [] } };

function preview(over: object = {}) {
  return { items: [], replacements: [], issues: [], checked: true, undoable: false, ...over };
}

async function tools(routes: Parameters<typeof fakeFetch>[0]) {
  const { fetch, calls } = fakeFetch(routes);
  const { auth } = signedInAuth(fetch);
  const root = await mkdtemp(join(tmpdir(), 'dw-copilot-test-'));
  return { tools: new DwTools(new DwClient(auth, fetch), root), calls, root };
}

test('a 401 is retried once with the refreshed token', async () => {
  let first = true;
  const { tools: t, calls } = await tools({
    'GET /api/v1/function-units/my-dev-groups': () => {
      if (first) { first = false; return json(401, {}); }
      return json(200, { success: true, data: { publicGroupId: 'vg-dev-public' } });
    },
    'POST /api/v1/auth/refresh': () => json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' }),
  });

  assert.match(await t.listWorkspaces(ORIGIN), /vg-dev-public/);
  assert.deepEqual(calls.filter(c => c.url.includes('my-dev-groups')).map(c => c.headers.Authorization),
    ['Bearer access-1', 'Bearer access-2']);
});

test('server validation errors reach the model with code and fieldPath', async () => {
  const { tools: t } = await tools({
    'POST /api/v1/ai-generation/studio-chat/dry-run': () => json(422, {
      success: false,
      error: { code: 'AI_STUDIO_PROPOSAL_EMPTY', message: 'no slices', details: { fieldPath: 'x' } },
    }),
  });

  const text = await asToolText(() => t.dryRun(ORIGIN, input));
  assert.match(text, /HTTP 422 AI_STUDIO_PROPOSAL_EMPTY: no slices/);
  assert.match(text, /fieldPath/);
});

test('apply is not prepared when the dry run reports an ERROR, and no backup is taken', async () => {
  const { tools: t, calls } = await tools({
    'POST /api/v1/ai-generation/studio-chat/dry-run': () => json(200, { success: true, data: preview({
      issues: [{ severity: 'ERROR', fieldPath: 'tableDefinitions[0].fieldDefinitions[2].dataType', description: 'Invalid enum value: MONEY' }],
    }) }),
  });

  const prepared = await t.prepareApply(ORIGIN, input);

  assert.equal(prepared.ok, false);
  assert.match(prepared.ok ? '' : prepared.text, /fieldDefinitions\[2\]\.dataType/);
  assert.ok(!calls.some(c => c.url.includes('/export')));
});

test('apply is not prepared when the dry run could not finish', async () => {
  const { tools: t } = await tools({
    'POST /api/v1/ai-generation/studio-chat/dry-run': () => json(200, { success: true, data: preview({ checked: false }) }),
  });

  const prepared = await t.prepareApply(ORIGIN, input);
  assert.equal(prepared.ok, false);
  assert.match(prepared.ok ? '' : prepared.text, /could not finish/);
});

test('a clean dry run backs up the current design before anything is written', async () => {
  const zip = new Uint8Array([0x50, 0x4b, 3, 4]);
  const { tools: t, calls, root } = await tools({
    'POST /api/v1/ai-generation/studio-chat/dry-run': () => json(200, { success: true, data: preview({
      items: [{ slice: 'tableDefinitions', name: 'travel_request', action: 'REPLACE' }],
      replacements: [{ slice: 'tableDefinitions', replacesExisting: 1 }],
    }) }),
    'GET /api/v1/export-import/function-units/388/export': () => new Response(zip, { status: 200 }),
    'POST /api/v1/ai-generation/studio-chat/apply': () => json(200, { success: true, data: { undoToken: null } }),
  });

  const prepared = await t.prepareApply(ORIGIN, input);
  assert.ok(prepared.ok);
  assert.ok(prepared.backupPath.startsWith(join(root, 'dw.example.test', '388')));
  assert.deepEqual(new Uint8Array(await readFile(prepared.backupPath)), zip);
  assert.match(prepared.summary, /REPLACE tableDefinitions: travel_request/);
  assert.match(prepared.summary, /replaces 1 existing/);
  assert.ok(!calls.some(c => c.url.endsWith('/apply')), 'prepare never writes');

  const result = await t.apply(ORIGIN, input, prepared.backupPath);
  assert.match(result, /Applied TABLES to function unit 388/);
  assert.match(result, /No undo token/);
});

test('DwApiError text carries status and code', () => {
  assert.equal(new DwApiError(409, 'AI_LOCKED', 'locked', undefined).toToolText(), 'Error: HTTP 409 AI_LOCKED: locked');
});
