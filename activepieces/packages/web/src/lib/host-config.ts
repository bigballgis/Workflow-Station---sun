// HERMES L1 (builder embedding): runtime config injected by a non-AP host — the
// Developer Workstation — so the vendored AP builder can mount inside DW without
// its standalone assumptions (localStorage token, same-origin /api, /sign-in
// redirects). The host sets `window.__AP_HOST_CONFIG__` before mounting; every
// field is optional and falls back to standalone AP behaviour, so the stock AP
// app is unchanged when no host config is present. Reads are lazy (evaluated at
// call time) so config set after the bundle's modules load still applies.

export const apHost = {
  getConfig(): ApHostConfig {
    if (typeof window === 'undefined') {
      return {};
    }
    return (
      (window as unknown as Record<string, ApHostConfig | undefined>)[
        '__AP_HOST_CONFIG__'
      ] ?? {}
    );
  },
  // #3 full REST API base incl. the /api prefix (e.g. `${origin}/api/ap` behind Kong).
  getApiUrl(): string {
    return apHost.getConfig().apiUrl ?? `${defaultOrigin()}/api`;
  },
  // #5 socket.io server origin.
  getSocketBaseUrl(): string {
    return apHost.getConfig().socketBaseUrl ?? defaultOrigin();
  },
  // #5 socket.io path (e.g. '/api/ap/socket.io' behind Kong).
  getSocketPath(): string {
    return apHost.getConfig().socketPath ?? '/api/socket.io';
  },
  // #7 Radix portal target. Under a shadow-root embed, portals must NOT fall back
  // to document.body: the Tailwind stylesheet lives inside the shadow root, so a
  // body-mounted popover/dialog renders unstyled and stacks under the host UI.
  getPortalContainer(): HTMLElement | undefined {
    return apHost.getConfig().portalContainer;
  },
};

function defaultOrigin(): string {
  return import.meta.env.MODE === 'cloud'
    ? 'https://cloud.activepieces.com'
    : window.location.origin;
}

export type ApHostConfig = {
  // #1 storage backing token/projectId (default window.localStorage).
  storage?: Storage;
  // #3 full REST API base incl. /api prefix (default `${origin}/api`).
  apiUrl?: string;
  // #5 socket.io server origin (default window.location.origin).
  socketBaseUrl?: string;
  // #5 socket.io path (default '/api/socket.io').
  socketPath?: string;
  // #2/#4 invoked instead of redirecting to /sign-in on session-expired / invalid token.
  onUnauthorized?: (reason: string) => void;
  // #7 element Radix portals render into (default document.body). A shadow-root
  // host passes its in-shadow container so portalled UI keeps the shadow styles.
  portalContainer?: HTMLElement;
  // Builder-embed seed: when mounted via lib-mode (not the postMessage iframe SDK),
  // the host seeds the EmbeddingProvider initial state here (isEmbedded + the chrome
  // switches: hideSideNav / disableNavigationInBuilder / hidePageHeader / …). Kept as
  // a loose partial to avoid a dependency cycle with embed-provider; it is spread onto
  // the typed EmbeddingState default there.
  embedding?: Record<string, unknown>;
};
