import assert from 'node:assert/strict';
import { buildPortalRequest } from './portal-client';

assert.throws(() => buildPortalRequest({
  baseUrl: 'http://user-portal:8080',
  token: 'portal-token',
  externalProjectId: 'hermes-dg-letters',
  flowId: 'flow-1',
  call: {
    method: 'PATCH',
    path: '/internal/automation/records/pi-1/fields',
    body: { lockVersion: 3, fields: { status: 'OPEN' }, actor: '林可' },
  },
}));

const request = buildPortalRequest({
  baseUrl: 'http://user-portal:8080',
  token: 'portal-token',
  externalProjectId: 'hermes-dg-letters',
  flowId: 'flow-1',
  call: {
    method: 'PATCH',
    path: '/internal/automation/records/pi-1/fields',
    idempotencyKey: 'run-1',
    body: { lockVersion: 3, fields: { status: 'OPEN' } },
  },
});

assert.equal(request.init.method, 'PATCH');
assert.equal((request.init.headers as Record<string, string>)['X-Internal-Token'], 'portal-token');
const body = JSON.parse(String(request.init.body));
assert.equal(body.actor, undefined);
assert.equal(body.externalProjectId, 'hermes-dg-letters');
assert.equal(body.flowId, 'flow-1');
assert.equal(body.fields.status, 'OPEN');

assert.throws(() => buildPortalRequest({
  baseUrl: 'http://user-portal:8080',
  token: 'portal-token',
  externalProjectId: 'hermes-dg-letters',
  flowId: 'flow-1',
  call: {
    method: 'POST',
    path: '/internal/automation/processes/start',
    body: { functionUnitCode: 'FU1', fields: {}, startUserId: 'u-1' },
  },
}));

assert.throws(() => buildPortalRequest({
  baseUrl: 'http://user-portal:8080',
  token: 'portal-token',
  externalProjectId: '',
  flowId: 'flow-1',
  call: { method: 'GET', path: '/internal/automation/records/pi-1' },
}));

console.log('portal-client tests passed');
