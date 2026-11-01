import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DwAuth, NoDwAccessError, NotSignedInError } from '../src/auth';
import { admission, fakeFetch, json, memorySecrets, ORIGIN, signedInAuth } from './fakes';

test('sign-in opens the unified login page and exchanges the code it gets back', async () => {
  const { fetch, calls } = fakeFetch({
    'POST /api/v1/auth/sso/exchange': () =>
      json(200, { accessToken: 'a', refreshToken: 'r', user: { username: 'dev1' } }),
    ...admission(['DEVELOPER']),
  });
  const opened: string[] = [];
  const secrets = memorySecrets();
  const auth = new DwAuth({
    secrets, fetch, callbackBase: 'vscode://workflow-station.dw-copilot',
    openExternal: async url => { opened.push(url); return true; },
  });

  const signIn = auth.signIn(ORIGIN);
  await new Promise(r => setImmediate(r));
  const login = new URL(opened[0]);
  assert.equal(login.origin + login.pathname, `${ORIGIN}/login/`);
  assert.equal(login.searchParams.get('client_id'), 'developer-workstation');
  assert.equal(login.searchParams.get('redirect_uri'), 'vscode://workflow-station.dw-copilot/sso-callback');

  await auth.handleCallback(`code=c1&state=${login.searchParams.get('state')}`);
  const session = await signIn;

  assert.equal(session.username, 'dev1');
  assert.equal(session.readOnly, false);
  assert.equal(JSON.parse(calls[0].body!).code, 'c1');
  assert.equal(await auth.accessToken(ORIGIN), 'a');
});

test('a callback whose state this window did not issue is rejected and never exchanged', async () => {
  const { fetch, calls } = fakeFetch({});
  const { auth } = signedInAuth(fetch);

  await assert.rejects(auth.handleCallback('code=stolen&state=forged'), /did not start/);
  assert.equal(calls.length, 0);
});

test('refresh stores the rotated pair as a whole', async () => {
  const { fetch, calls } = fakeFetch({
    'POST /api/v1/auth/refresh': () => json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' }),
    ...admission(['DEVELOPER']),
  });
  const { auth } = signedInAuth(fetch);

  assert.equal(await auth.refresh(ORIGIN), 'access-2');
  assert.equal(JSON.parse(calls[0].body!).refreshToken, 'refresh-1');
  const session = await auth.session(ORIGIN);
  assert.deepEqual([session?.accessToken, session?.refreshToken, session?.username], ['access-2', 'refresh-2', 'dev1']);
});

test('an expired refresh token signs the user out', async () => {
  const { fetch } = fakeFetch({ 'POST /api/v1/auth/refresh': () => json(401, {}) });
  const { auth } = signedInAuth(fetch);

  await assert.rejects(auth.refresh(ORIGIN), NotSignedInError);
  assert.equal(await auth.session(ORIGIN), undefined);
});

test('a server error during refresh keeps the session for the next attempt', async () => {
  const { fetch } = fakeFetch({ 'POST /api/v1/auth/refresh': () => json(503, {}) });
  const { auth } = signedInAuth(fetch);

  await assert.rejects(auth.refresh(ORIGIN), /HTTP 503/);
  assert.equal((await auth.session(ORIGIN))?.refreshToken, 'refresh-1');
});

test('parallel 401s share one refresh instead of spending the rotating token twice', async () => {
  let refreshes = 0;
  const { fetch } = fakeFetch({
    'POST /api/v1/auth/refresh': () => {
      refreshes++;
      return refreshes === 1 ? json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' }) : json(401, {});
    },
    ...admission(['DEVELOPER']),
  });
  const { auth } = signedInAuth(fetch);

  const tokens = await Promise.all([auth.refresh(ORIGIN), auth.refresh(ORIGIN), auth.refresh(ORIGIN)]);

  assert.deepEqual(tokens, ['access-2', 'access-2', 'access-2']);
  assert.equal(refreshes, 1);
  assert.equal((await auth.session(ORIGIN))?.refreshToken, 'refresh-2');
});

/** Signs in through the browser callback with the given admission routes; returns the outcome and stored secrets. */
async function signInWith(routes: Parameters<typeof fakeFetch>[0]) {
  const { fetch, calls } = fakeFetch({
    'POST /api/v1/auth/sso/exchange': () => json(200, { accessToken: 'a', refreshToken: 'r', user: { username: 'u1' } }),
    ...routes,
  });
  const opened: string[] = [];
  const secrets = memorySecrets();
  const auth = new DwAuth({
    secrets, fetch, callbackBase: 'vscode://workflow-station.dw-copilot',
    openExternal: async url => { opened.push(url); return true; },
  });
  const signIn = auth.signIn(ORIGIN);
  await new Promise(r => setImmediate(r));
  await auth.handleCallback(`code=c1&state=${new URL(opened[0]).searchParams.get('state')}`);
  const outcome = await signIn.then(session => ({ session }), (error: Error) => ({ error }));
  return { outcome, secrets, calls };
}

test('a DW capability role signs in with full access', async () => {
  const { outcome, calls } = await signInWith(admission(['TEAM_LEAD']));

  assert.equal('session' in outcome && outcome.session.readOnly, false);
  assert.equal(calls.some(c => c.url.includes('workspace-access')), false);
  assert.equal(calls.find(c => c.url.endsWith('/api/v1/auth/me'))?.headers.Authorization, 'Bearer a');
});

test('a team member without a DW role signs in read-only, as the DW web workspace fallback does', async () => {
  const { outcome } = await signInWith(admission([], true));

  assert.equal('session' in outcome && outcome.session.readOnly, true);
});

test('a pure AUDITOR is refused even though workspace-access would admit them', async () => {
  const { outcome, secrets, calls } = await signInWith(admission(['AUDITOR'], true));

  assert.ok('error' in outcome && outcome.error instanceof NoDwAccessError);
  assert.equal(secrets.data.size, 0);
  assert.equal(calls.some(c => c.url.includes('workspace-access')), false);
});

test('an AUDITOR who also holds a DW role signs in', async () => {
  const { outcome } = await signInWith(admission(['AUDITOR', 'DEVELOPER']));

  assert.equal('session' in outcome && outcome.session.readOnly, false);
});

test('a user who cannot enter DW is refused and nothing is stored', async () => {
  const { outcome, secrets } = await signInWith(admission(['PORTAL_USER'], false));

  assert.ok('error' in outcome && outcome.error instanceof NoDwAccessError);
  assert.equal(secrets.data.size, 0);
});

test('a role revoked since sign-in signs the user out at the next refresh', async () => {
  const { fetch } = fakeFetch({
    'POST /api/v1/auth/refresh': () => json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' }),
    ...admission([], false),
  });
  const { auth } = signedInAuth(fetch);

  await assert.rejects(auth.refresh(ORIGIN), NoDwAccessError);
  assert.equal(await auth.session(ORIGIN), undefined);
});

test('startup re-checks admission and refreshes an expired access token first', async () => {
  const { fetch, calls } = fakeFetch({
    'GET /api/v1/auth/me': call => call.headers.Authorization === 'Bearer access-1'
      ? json(401, {}) : json(200, { roles: [], username: 'dev1' }),
    'GET /api/v1/function-units/workspace-access': () => json(200, { success: true, data: { canView: true } }),
    'POST /api/v1/auth/refresh': () => json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' }),
  });
  const { auth } = signedInAuth(fetch);

  const session = await auth.verify(ORIGIN);

  assert.deepEqual([session?.accessToken, session?.readOnly], ['access-2', true]);
  assert.equal(calls.filter(c => c.url.includes('/auth/refresh')).length, 1);
});

test('a DW outage during the startup check keeps the session', async () => {
  const { fetch } = fakeFetch({ 'GET /api/v1/auth/me': () => json(503, {}) });
  const { auth } = signedInAuth(fetch);

  await assert.rejects(auth.verify(ORIGIN), /HTTP 503/);
  assert.equal((await auth.session(ORIGIN))?.refreshToken, 'refresh-1');
});

test('a refresh that lands during the startup check is not overwritten by the older pair', async () => {
  let release!: () => void;
  const oldTokenCheck = new Promise<void>(r => { release = r; });
  const { fetch } = fakeFetch({
    // The startup check (old token) is slow; a tool call's refresh rotates the pair and finishes first.
    'GET /api/v1/auth/me': async call => {
      if (call.headers.Authorization === 'Bearer access-1') await oldTokenCheck;
      return json(200, { roles: ['DEVELOPER'] });
    },
    'POST /api/v1/auth/refresh': () => json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' }),
  });
  const { auth } = signedInAuth(fetch);

  const checking = auth.verify(ORIGIN);
  await new Promise(r => setImmediate(r));
  await auth.refresh(ORIGIN);
  release();
  await checking;

  assert.equal((await auth.session(ORIGIN))?.refreshToken, 'refresh-2');
});
