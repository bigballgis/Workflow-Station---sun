import { PageTitle } from '@/app/components/page-title';

import { SignInPage } from './sign-in';

// HERMES FR-D2: sign-up / forget-password / change-password / create-platform /
// invitation flows are removed — identity is owned by HERMES SSO (per-user tokens
// minted via the L7 managed-authn bridge).
//
// HERMES-PATCH-028: `/verify-email` removed too. It called
// `POST /v1/authn/local/verify-email`, and this fork registers no `/v1/authn` prefix at
// all — measured 404 against a 400 control on a live POST route. The page could only ever
// show its failure state.
//
// `/sign-in` stays as a PATH but no longer carries a credential form: five guards navigate
// here on a missing/expired session, and they need somewhere that explains what to do. See
// the comment in ./sign-in.
export const authRoutes = [
  {
    path: '/sign-in',
    element: (
      <PageTitle title="Session Expired">
        <SignInPage />
      </PageTitle>
    ),
  },
];
