import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DwAuth, NotSignedInError } from '../src/auth';
import { fakeFetch, json, memorySecrets, ORIGIN, signedInAuth } from './fakes';

test('sign-in opens the unified login page and exchanges the code it gets back', async () => {
  const { fetch, calls } = fakeFetch({
    'POST /api/v1/auth/sso/exchange': () =>
      json(200, { accessToken: 'a', refreshToken: 'r', user: { username: 'dev1' } }),
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
  });
  const { auth } = signedInAuth(fetch);

  const tokens = await Promise.all([auth.refresh(ORIGIN), auth.refresh(ORIGIN), auth.refresh(ORIGIN)]);

  assert.deepEqual(tokens, ['access-2', 'access-2', 'access-2']);
  assert.equal(refreshes, 1);
  assert.equal((await auth.session(ORIGIN))?.refreshToken, 'refresh-2');
});
