import React, { useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { LoadingScreen } from '@/components/custom/loading-screen';

/**
 * OAuth2 popup callback.
 *
 * HERMES-PATCH-028: the third-party *login* branch is gone. It claimed an identity via
 * `POST /v1/authn/federated/claim` — a prefix this fork does not register (measured: 404,
 * against a 400 control on a live POST route) — and on success navigated to
 * `/create-platform`, a route removed with FR-D2. Both ends were dead; only the toast in
 * between still worked.
 *
 * What remains is the piece-connection OAuth flow, which is very much alive: a connection
 * dialog opens the provider's consent screen in a popup, the provider redirects here with
 * `?code=`, and this page hands the code back to the opener
 * (features/connections/utils/oauth2-utils.ts listens for it). That path never touches
 * platform identity — it is per-connection credentials for a piece.
 */
const RedirectPage: React.FC = React.memo(() => {
  const location = useLocation();
  const navigate = useNavigate();
  const hasCheckedParams = useRef(false);
  useEffect(() => {
    if (hasCheckedParams.current) {
      return;
    }
    hasCheckedParams.current = true;
    const params = new URLSearchParams(location.search);
    const code = params.get('code');

    if (window.opener && code) {
      window.opener.postMessage(
        {
          code: code,
        },
        '*',
      );
    }
    if (!window.opener && !code) {
      navigate('/');
    }
  }, [location.search]);

  return <LoadingScreen />;
});

RedirectPage.displayName = 'RedirectPage';
export { RedirectPage };
