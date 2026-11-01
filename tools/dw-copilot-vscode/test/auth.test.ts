import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DwAuth, listenLoopback, NoDwAccessError, NotSignedInError } from '../src/auth';
import { admission, fakeFetch, fakeLoopback, json, memorySecrets, ORIGIN, signedInAuth } from './fakes';

/** Starts sign-in against an in-memory loopback listener; resolves once the login page has been opened. */
async function startSignIn(routes: Parameters<typeof fakeFetch>[0], openResult = true) {
  const { fetch, calls } = fakeFetch(routes);
  const opened: string[] = [];
  const secrets = memorySecrets();
  const loopback = fakeLoopback();
  const auth = new DwAuth({
    secrets, fetch, listenLoopback: loopback.listenLoopback,
    openExternal: async url => { opened.push(url); return openResult; },
  });
  const signIn = auth.signIn(ORIGIN);
  const outcome = signIn.then(session => ({ session }), (error: Error) => ({ error }));
  await new Promise(r => setImmediate(r));
  const login = opened[0] ? new URL(opened[0]) : undefined;
  return { auth, outcome, login, state: login?.searchParams.get('state'), loopback, secrets, calls };
}

const exchangeOk = {
  'POST /api/v1/auth/sso/exchange': () => json(200, { accessToken: 'a', refreshToken: 'r', user: { username: 'dev1' } }),
};

test('sign-in opens the unified login page with a 127.0.0.1 callback and exchanges the code it gets back', async () => {
  const { auth, outcome, login, state, loopback, calls } = await startSignIn({ ...exchangeOk, ...admission(['DEVELOPER']) });
  assert.equal(login!.origin + login!.pathname, `${ORIGIN}/login/`);
  assert.equal(login!.searchParams.get('client_id'), 'developer-workstation');
  assert.equal(login!.searchParams.get('redirect_uri'), 'http://127.0.0.1:54321/sso-callback');

  const reply = await loopback.request(`/sso-callback?code=c1&state=${state}`);
  const result = await outcome;

  assert.equal(reply.status, 200);
  assert.match(reply.html, /Signed in to DW as dev1/);
  assert.ok('session' in result && result.session.readOnly === false);
  assert.equal(JSON.parse(calls[0].body!).code, 'c1');
  assert.equal(await auth.accessToken(ORIGIN), 'a');
  assert.equal(loopback.state.closed, true);
});

test('a callback with another state or path is refused, never exchanged, and the real one still completes', async () => {
  const { outcome, state, loopback, calls } = await startSignIn({ ...exchangeOk, ...admission(['DEVELOPER']) });

  assert.equal((await loopback.request('/sso-callback?code=stolen&state=forged')).status, 400);
  assert.equal((await loopback.request(`/other?code=c1&state=${state}`)).status, 404);
  assert.equal((await loopback.request('/favicon.ico')).status, 404);
  assert.equal(calls.length, 0);
  assert.equal(loopback.state.closed, false);

  assert.equal((await loopback.request(`/sso-callback?code=c1&state=${state}`)).status, 200);
  assert.ok('session' in (await outcome));
});

test('the callback is used once: a replay of the same state is refused', async () => {
  const { outcome, state, loopback, calls } = await startSignIn({ ...exchangeOk, ...admission(['DEVELOPER']) });

  const first = loopback.request(`/sso-callback?code=c1&state=${state}`);
  const replay = await loopback.request(`/sso-callback?code=c2&state=${state}`);
  await first;
  await outcome;

  assert.equal(replay.status, 400);
  assert.equal(calls.filter(c => c.url.endsWith('/sso/exchange')).length, 1);
});

test('a failed exchange shows the error in the browser, rejects sign-in and closes the listener', async () => {
  const { outcome, state, loopback, secrets } = await startSignIn({
    'POST /api/v1/auth/sso/exchange': () => json(400, {}),
  });

  const reply = await loopback.request(`/sso-callback?code=expired&state=${state}`);
  const result = await outcome;

  assert.equal(reply.status, 400);
  assert.match(reply.html, /DW sign-in failed: DW \/api\/v1\/auth\/sso\/exchange failed: HTTP 400/);
  assert.ok('error' in result);
  assert.equal(secrets.data.size, 0);
  assert.equal(loopback.state.closed, true);
});

test('a callback without a code rejects sign-in', async () => {
  const { outcome, state, loopback, calls } = await startSignIn({});

  assert.equal((await loopback.request(`/sso-callback?state=${state}`)).status, 400);
  const result = await outcome;
  assert.ok('error' in result && /no code/.test(result.error.message));
  assert.equal(calls.length, 0);
});

test('if the browser is not opened, sign-in fails and the listener is closed', async () => {
  const { outcome, loopback } = await startSignIn({}, false);

  const result = await outcome;
  assert.ok('error' in result && /browser was not opened/.test(result.error.message));
  assert.equal(loopback.state.closed, true);
});

test('a listener that cannot start fails sign-in before the browser opens', async () => {
  const opened: string[] = [];
  const auth = new DwAuth({
    secrets: memorySecrets(), fetch: fakeFetch({}).fetch,
    listenLoopback: async () => { throw new Error('DW sign-in could not open a local port on 127.0.0.1'); },
    openExternal: async url => { opened.push(url); return true; },
  });

  await assert.rejects(auth.signIn(ORIGIN), /could not open a local port on 127\.0\.0\.1/);
  assert.equal(opened.length, 0);
});

test('sign-in times out after 5 minutes, names the callback address and closes the listener', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const { outcome, loopback } = await startSignIn({});

  t.mock.timers.tick(5 * 60 * 1000);
  const result = await outcome;

  assert.ok('error' in result && /timed out after 5 minutes/.test(result.error.message));
  assert.match((result as { error: Error }).error.message, /http:\/\/127\.0\.0\.1:54321\/sso-callback/);
  assert.equal(loopback.state.closed, true);
});

test('the real loopback listener binds 127.0.0.1, serves the callback page over HTTP and then closes', async () => {
  const { fetch: dwFetch } = fakeFetch({ ...exchangeOk, ...admission(['DEVELOPER']) });
  const opened: string[] = [];
  const auth = new DwAuth({
    secrets: memorySecrets(), fetch: dwFetch, listenLoopback,
    openExternal: async url => { opened.push(url); return true; },
  });

  const signIn = auth.signIn(ORIGIN);
  while (opened.length === 0) await new Promise(r => setImmediate(r));
  const login = new URL(opened[0]);
  const callback = new URL(login.searchParams.get('redirect_uri')!);
  assert.equal(callback.hostname, '127.0.0.1');
  assert.ok(Number(callback.port) > 0);

  const notFound = await fetch(new URL('/favicon.ico', callback));
  assert.equal(notFound.status, 404);
  const res = await fetch(`${callback}?code=c1&state=${login.searchParams.get('state')}`);
  assert.equal(res.status, 200);
  assert.match(res.headers.get('content-type') ?? '', /text\/html/);
  assert.match(await res.text(), /You can close this tab/);
  assert.equal((await signIn).username, 'dev1');

  await assert.rejects(fetch(callback), /fetch failed/);
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
  const started = await startSignIn({
    'POST /api/v1/auth/sso/exchange': () => json(200, { accessToken: 'a', refreshToken: 'r', user: { username: 'u1' } }),
    ...routes,
  });
  await started.loopback.request(`/sso-callback?code=c1&state=${started.state}`);
  return { outcome: await started.outcome, secrets: started.secrets, calls: started.calls };
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
