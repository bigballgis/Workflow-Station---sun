import { ProjectRole } from '@activepieces/core-utils';
import {
  AuthenticationResponse,
  GetCurrentProjectMemberRoleQuery,
  SwitchPlatformRequest,
} from '@activepieces/shared';

import { api } from '@/lib/api';

/**
 * HERMES-PATCH-028: reduced to the two calls that still reach a registered route.
 *
 * Removed, with the reason each was dead:
 * - `signIn` / `signUp` — the credential path. Identity is minted per actor by
 *   admin-center via managed-authn; every gateway in front of AP now terminates
 *   `POST /v1/authentication/sign-{in,up}` with 404.
 * - `getFederatedAuthLoginUrl`, `claimThirdPartyRequest`, `resetPassword`, `verifyEmail`
 *   — all under `/v1/authn/*`, a prefix this fork registers nowhere. Measured: 404 on
 *   each, against a 400 control on a live POST route, so the 404s are "no such route"
 *   rather than "bad request".
 * - `sendOtpEmail` — `POST /v1/otp`, removed with the OTP/email-identity domain.
 *
 * `switchPlatform` stays (lib/authentication-session.ts) and `getCurrentProjectRole`
 * stays (hooks/authorization-hooks.ts); note the latter is not an authn route at all —
 * it lives under `/v1/project-members`.
 */
export const authenticationApi = {
  getCurrentProjectRole(query: GetCurrentProjectMemberRoleQuery) {
    return api.get<ProjectRole | null>('/v1/project-members/role', query);
  },
  switchPlatform(request: SwitchPlatformRequest) {
    return api.post<AuthenticationResponse>(
      `/v1/authentication/switch-platform`,
      request,
    );
  },
};
