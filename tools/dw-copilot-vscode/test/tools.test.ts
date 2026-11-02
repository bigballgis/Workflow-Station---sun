import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { DwApiError, DwClient } from '../src/dwClient';
import { ApplyInput, asToolText, DwTools, PAGE_CHARS, paged, readOnlyHandlers } from '../src/tools';
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
    'GET /api/v1/auth/me': () => json(200, { username: 'dev1', roles: ['DEVELOPER'] }),
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

function stripHeader(part: string): string {
  return part.replace(/^\[Part \d+ of \d+\.[^\]]*\]\n/, '');
}

test('output that fits in one part is returned unchanged', () => {
  assert.equal(paged('small'), 'small');
});

test('long output is split into parts that each fit and rejoin exactly', () => {
  const text = Array.from({ length: 900 }, (_, i) => `line ${i} ${'x'.repeat(i % 50)}`).join('\n') +
    '\n' + 'y'.repeat(PAGE_CHARS * 2 + 7);
  const first = paged(text, 1);
  const total = Number(/of (\d+)/.exec(first)![1]);
  const parts = Array.from({ length: total }, (_, i) => paged(text, i + 1));

  assert.ok(total > 2);
  assert.match(parts[0], /"page": 2 for the next part/);
  assert.match(parts[total - 1], /This is the last part/);
  for (const p of parts) assert.ok(stripHeader(p).length <= PAGE_CHARS + 1);
  assert.equal(parts.map(stripHeader).join(''), text);
  assert.match(paged(text, total + 1), /page must be between 1 and/);
});

const design = {
  functionUnitId: 388, name: 'P0', description: 'd',
  tableDefinitions: [{ tableName: 'travel_request', fieldDefinitions: [{ fieldName: 'id' }] }],
  tableRelations: [],
  processDefinition: { processKey: 'p', bpmnXml: '<xml/>' },
  orgCatalog: { roles: [], businessUnits: [] },
};

test('the design without sections is a table of contents', async () => {
  const { tools: t } = await tools({
    'GET /api/v1/ai-generation/studio-chat/design-context': () => json(200, { success: true, data: design }),
  });

  const toc = JSON.parse((await t.getDesign(ORIGIN, 388)).split('\nCall again')[0]);

  assert.deepEqual(toc.sections.tableDefinitions, { count: 1, names: ['travel_request'] });
  assert.deepEqual(toc.sections.tableRelations, { count: 0 });
  assert.deepEqual(toc.sections.orgCatalog, { keys: ['roles', 'businessUnits'] });
  assert.equal(JSON.stringify(toc).includes('fieldDefinitions'), false);
});

test('the design with sections returns just those sections in full', async () => {
  const { tools: t } = await tools({
    'GET /api/v1/ai-generation/studio-chat/design-context': () => json(200, { success: true, data: design }),
  });

  const part = JSON.parse(await t.getDesign(ORIGIN, 388, ['tableDefinitions', 'tableRelations']));

  assert.deepEqual(part.tableDefinitions, design.tableDefinitions);
  assert.deepEqual(part.tableRelations, []);
  assert.equal('processDefinition' in part, false);
  assert.match(await t.getDesign(ORIGIN, 388, ['tables']), /unknown section\(s\) tables\. Valid: tableDefinitions/);
});

test('the read-only handler serves a large format spec in parts', async () => {
  const template = Array.from({ length: 400 }, (_, i) => `rule ${i}: ${'z'.repeat(60)}`).join('\n');
  const { tools: t } = await tools({
    'GET /api/v1/ai-generation/studio-chat/format-spec': () =>
      json(200, { success: true, data: { template, schemaMetadata: { tableTypes: ['MAIN'] } } }),
  });
  const spec = readOnlyHandlers({ origin: () => ORIGIN, tools: t, exportFolder: () => '' }).dw_get_format_spec;

  const first = await spec({});
  const total = Number(/of (\d+)/.exec(first)![1]);
  const last = await spec({ page: total });

  assert.match(first, /^\[Part 1 of \d+\./);
  assert.match(last, /schemaMetadata \(authoritative/);
  assert.match(last, /"MAIN"/);
});

test('the page argument never reaches the dry-run request body', async () => {
  const { tools: t, calls } = await tools({
    'POST /api/v1/ai-generation/studio-chat/dry-run': () => json(200, { success: true, data: preview() }),
  });
  const dryRun = readOnlyHandlers({ origin: () => ORIGIN, tools: t, exportFolder: () => '' }).dw_dry_run;

  await dryRun({ ...input, page: 1 });

  assert.deepEqual(JSON.parse(calls[0].body!), input);
});
