import { t } from 'i18next';

import { Button } from '@/components/ui/button';

/**
 * HERMES-PATCH-028: Activepieces has no local sign-in any more.
 *
 * Identity is "whoever acts, that's the identity": an AP session is minted only by
 * admin-center, per actor, through the managed-authn exchange. The credential form that
 * used to live here posted to `POST /v1/authentication/sign-{in,up}`, which every gateway
 * in front of AP now terminates with 404 (Kong `activepieces-authn-block-route`, the dev
 * edge nginx, and the k8s VirtualService). Keeping the form would have meant shipping a
 * login box whose submit button can only ever fail.
 *
 * This route is NOT deleted, because five guards still navigate here when a session is
 * missing or expired (allow-logged-in-user-only-guard, project-layout, default-route,
 * project-route-wrapper ×2). Removing the route would land them on the 404 page, which
 * says nothing about what to do next. So the path stays and explains itself instead.
 *
 * The embedded builder never reaches this page: hosts pass `onUnauthorized` through
 * host-config (injection points #2/#4) and handle re-authentication themselves.
 *
 * `/__ap/bridge` is served by BOTH gateways that expose AP to a browser — the dev edge
 * (nginx `location /__ap/bridge`) and k8s (`ap-gateway.yaml` VirtualService, exact match).
 * It re-mints this user's own AP token when the platform cookie is present, and otherwise
 * bounces to the unified login. There is no third gateway, so this link is not
 * environment-specific.
 */
const SignInPage: React.FC = () => {
  return (
    <div className="flex h-screen w-screen items-center justify-center">
      <div className="mx-auto flex w-full max-w-md flex-col items-center gap-4 px-6 text-center">
        <h1 className="text-2xl font-semibold">{t('Session expired')}</h1>
        <p className="text-muted-foreground text-sm">
          {t(
            'Automation has no separate login — you are signed in as whoever opened it. Return through Workflow Station to continue.',
          )}
        </p>
        <Button onClick={() => window.location.assign('/__ap/bridge')}>
          {t('Return to Workflow Station')}
        </Button>
      </div>
    </div>
  );
};

SignInPage.displayName = 'SignInPage';

export { SignInPage };
