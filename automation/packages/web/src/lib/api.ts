import { ApErrorParams, ErrorCode, isNil } from '@activepieces/core-utils';
import axios, {
  AxiosError,
  AxiosRequestConfig,
  AxiosResponse,
  HttpStatusCode,
  isAxiosError,
} from 'axios';
import qs from 'qs';

import { authenticationSession } from '@/lib/authentication-session';
import { apHost } from '@/lib/host-config';
// HERMES-PATCH-023: dropped `isRunningCloudInDevMode` (`import.meta.env.MODE ===
// 'cloud'`) and the cloud.activepieces.com base it selected. That mode
// is an upstream hosted-dev convenience — no config in this fork ever sets it —
// and the air-gapped deployment must never resolve an off-site API origin.
export const API_BASE_URL =
  typeof window !== 'undefined' ? window.location.origin : '';
// HERMES L1 (#3): default standalone base kept for compatibility, but the active
// request path resolves the base lazily via apHost.getApiUrl() so an embedding
// host (DW) can point REST at its gateway prefix (e.g. `${origin}/api/ap`).
export const API_URL = `${API_BASE_URL}/api`;

// Routes the client calls without a bearer token. HERMES-PATCH-021 (0.88) pruned
// every entry whose backend route no longer exists: `/v1/human-input` (deleted
// with humanInputModule), plus `/v1/otp`, `/v1/user-invitations/accept` and the
// four `/v1/authn/*` entries — no `/v1/authn`, `/v1/otp` or `/v1/user-invitations`
// prefix is registered anywhere in packages/server/api (those ee domains went
// with EE_REMOVAL_PLAN). The four survivors were each grep-confirmed live:
// managed-authn-module POST '/external-token' under prefix '/v1/managed-authn',
// authentication.controller POST '/sign-in' + '/sign-up' under '/v1/authentication',
// and webhook-module under '/v1/webhooks'.
const disallowedRoutes = [
  '/v1/managed-authn/external-token',
  '/v1/authentication/sign-in',
  '/v1/authentication/sign-up',
  '/v1/webhooks',
];
//This is important to avoid redirecting to sign-in page when the user is deleted for embedding scenarios
const ignroedGlobalErrorHandlerRoutes = ['/v1/users/me'];
function isUrlRelative(url: string) {
  return !url.startsWith('http') && !url.startsWith('https');
}

function globalErrorHandler(error: AxiosError) {
  if (api.isError(error)) {
    const errorCode: ErrorCode | undefined = (
      error.response?.data as { code: ErrorCode }
    )?.code;
    if (
      errorCode === ErrorCode.SESSION_EXPIRED ||
      errorCode === ErrorCode.INVALID_BEARER_TOKEN
    ) {
      // HERMES L1 (#4): an embedding host handles re-auth itself (it owns the
      // session and must NOT be navigated away to AP's /sign-in). Standalone AP
      // keeps the original logout-and-redirect.
      const onUnauthorized = apHost.getConfig().onUnauthorized;
      if (onUnauthorized) {
        onUnauthorized(errorCode);
      } else {
        authenticationSession.logOut();
        console.log(errorCode);
        window.location.href = '/sign-in';
      }
    }
  }
}

function request<TResponse>(
  url: string,
  config: AxiosRequestConfig = {},
): Promise<TResponse> {
  // HERMES L1 (#3): resolve the API base at call time so a host-injected gateway
  // prefix applies even though this module evaluated before the host set config.
  const apiUrl = apHost.getApiUrl();
  const resolvedUrl = !isUrlRelative(url) ? url : `${apiUrl}${url}`;
  const isApWebsite = resolvedUrl.startsWith(apiUrl);
  const unAuthenticated = disallowedRoutes.some((route) =>
    resolvedUrl.replace(apiUrl, '').startsWith(route),
  );

  return axios({
    url: resolvedUrl,
    ...config,
    headers: {
      ...config.headers,
      Authorization: getToken(
        unAuthenticated,
        isApWebsite,
        authenticationSession.getToken(),
      ),
    },
  })
    .then((response) =>
      config.responseType === 'blob'
        ? response.data
        : (response.data as TResponse),
    )
    .catch((error) => {
      if (
        isAxiosError(error) &&
        !ignroedGlobalErrorHandlerRoutes.includes(url)
      ) {
        globalErrorHandler(error);
      }
      throw error;
    });
}

function getToken(
  unAuthenticated: boolean,
  isApWebsite: boolean,
  token: string | null,
) {
  if (unAuthenticated || !isApWebsite) {
    return undefined;
  }
  if (isNil(token)) {
    return undefined;
  }
  return `Bearer ${token}`;
}

export type HttpError = AxiosError<unknown, AxiosResponse<unknown>>;

export const api = {
  isApError(error: unknown, errorCode: ErrorCode): error is HttpError {
    if (!isAxiosError(error)) {
      return false;
    }
    const responseData = error.response?.data as ApErrorParams;
    return responseData.code === errorCode;
  },
  isError(error: unknown): error is HttpError {
    return isAxiosError(error);
  },
  extractServerErrorMessage(error: unknown, fallback: string): string {
    if (api.isError(error)) {
      const data = error.response?.data as ApErrorParams | undefined;
      const message =
        data?.params && 'message' in data.params
          ? data.params.message
          : undefined;
      if (typeof message === 'string' && message.length > 0) {
        return message;
      }
    }
    if (error instanceof Error && error.message.length > 0) {
      return error.message;
    }
    return fallback;
  },
  any: <TResponse>(url: string, config?: AxiosRequestConfig) =>
    request<TResponse>(url, config),
  get: <TResponse>(url: string, query?: unknown, config?: AxiosRequestConfig) =>
    request<TResponse>(url, {
      params: query,
      paramsSerializer: (params) => {
        return qs.stringify(params, {
          arrayFormat: 'repeat',
        });
      },
      ...config,
    }),
  delete: <TResponse>(
    url: string,
    query?: Record<string, string>,
    body?: unknown,
  ) =>
    request<TResponse>(url, {
      method: 'DELETE',
      params: query,
      data: body,
      paramsSerializer: (params) => {
        return qs.stringify(params, {
          arrayFormat: 'repeat',
        });
      },
    }),
  post: <TResponse, TBody = unknown, TParams = unknown>(
    url: string,
    body?: TBody,
    params?: TParams,
    headers?: Record<string, string>,
  ) =>
    request<TResponse>(url, {
      method: 'POST',
      data: body,
      headers: { 'Content-Type': 'application/json', ...headers },
      params: params,
    }),

  patch: <TResponse, TBody = unknown, TParams = unknown>(
    url: string,
    body?: TBody,
    params?: TParams,
  ) =>
    request<TResponse>(url, {
      method: 'PATCH',
      data: body,
      headers: { 'Content-Type': 'application/json' },
      params: params,
    }),
  httpStatus: HttpStatusCode,
};
