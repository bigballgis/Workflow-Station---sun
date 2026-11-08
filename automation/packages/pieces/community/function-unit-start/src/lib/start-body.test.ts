import assert from 'node:assert/strict';
import { startRequestBody } from './start-body';

assert.throws(() => startRequestBody('  ', '{"status":"OPEN"}'));
assert.throws(() => startRequestBody('FU1', '['));
assert.throws(() => startRequestBody('FU1', '{"__subTables__":{}}'));

const body = startRequestBody(' FU1 ', '{"status":"OPEN"}');
assert.equal(body.functionUnitCode, 'FU1');
assert.equal((body.fields as { status: string }).status, 'OPEN');
assert.deepEqual(startRequestBody('FU1', '').fields, {});

console.log('start-body tests passed');
