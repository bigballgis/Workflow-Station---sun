import { SeekPage } from '@activepieces/core-utils';
import {
  ListOAuth2AppRequest,
  OAuthApp,
  UpsertOAuth2AppRequest,
} from '@activepieces/shared';

import { api } from '@/lib/api';

// HERMES-PATCH-023: `listCloudOAuth2Apps()` deleted. It GET'd
// secrets.activepieces.com/apps — Activepieces' hosted OAuth app
// registry. Our connections use self-hosted OAuth apps only (`/v1/oauth-apps`),
// and an air-gapped deployment cannot reach that host at all. Removing the call
// (rather than swallowing its failure) makes the CLOUD_OAUTH2 path stay empty
// by construction instead of failing at connect time.
export const oauthAppsApi = {
  listPlatformOAuth2Apps(request: ListOAuth2AppRequest) {
    return api.get<SeekPage<OAuthApp>>('/v1/oauth-apps', request);
  },
  delete(credentialId: string) {
    return api.delete<void>(`/v1/oauth-apps/${credentialId}`);
  },
  upsert(request: UpsertOAuth2AppRequest) {
    return api.post<OAuthApp>('/v1/oauth-apps', request);
  },
};
