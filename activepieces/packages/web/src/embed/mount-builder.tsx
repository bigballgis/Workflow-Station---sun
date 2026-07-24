// HERMES L1: lib-mode mount entry for embedding the AP builder inside the
// Developer Workstation (Vue 3) WITHOUT the postMessage iframe SDK (which the
// project rejected). The DW host calls mountApBuilder(config); this sets the
// host-config injection points (api base -> Kong /api/ap, socket path, per-user
// token storage), seeds the EmbeddingProvider (isEmbedded -> memoryRouter, so the
// host URL is never touched and standalone chrome is hidden), points the in-memory
// router at the builder route, and renders the React app into the given container
// (intended to be a Shadow root). Returns an unmount function.
import '../polyfills';
import '../styles.css';
import '../i18n';

import * as ReactDOM from 'react-dom/client';

import App from '../app/app';
import { memoryRouter } from '../app/guards';
import { ApHostConfig } from '../lib/host-config';

export function mountApBuilder(config: MountBuilderConfig): () => void {
  const storage = config.storage ?? window.localStorage;

  // 1) Host config must exist before any AP module reads it (storage/api/socket
  //    resolve lazily, so setting it here — even after this bundle loaded — applies).
  const hostConfig: ApHostConfig = {
    storage,
    apiUrl: config.apiUrl,
    socketBaseUrl: config.socketBaseUrl,
    socketPath: config.socketPath,
    onUnauthorized: config.onUnauthorized,
    disableBillingDialogs: true,
    embedding: {
      isEmbedded: true,
      hideSideNav: true,
      hideFlowsPageNavbar: true,
      hidePageHeader: true,
      disableNavigationInBuilder: config.disableNavigation ?? false,
      ...config.embedding,
    },
  };
  (window as unknown as Record<string, unknown>)['__AP_HOST_CONFIG__'] =
    hostConfig;

  // 2) Seed the per-user AP session the builder reads through ApStorage.
  storage.setItem('token', config.token);
  storage.setItem('projectId', config.projectId);

  // 3) Point the in-memory router at the builder route (no host-URL change).
  void memoryRouter.navigate(
    `/projects/${config.projectId}/flows/${config.flowId}`,
  );

  // 4) Mount the React app; ApRouter selects memoryRouter because isEmbedded.
  const root = ReactDOM.createRoot(config.container);
  root.render(<App />);
  return () => root.unmount();
}

export type MountBuilderConfig = {
  // Shadow root (or element) to render the builder into.
  container: HTMLElement | DocumentFragment;
  // Target flow + shared project (from L7 provisioning).
  flowId: string;
  projectId: string;
  // Per-user AP token minted by the L7 bridge (managed-authn exchange).
  token: string;
  // L2 Kong REST base incl. prefix, e.g. `${origin}/api/ap`.
  apiUrl: string;
  // L2 Kong socket.io, e.g. base = origin, path = '/api/ap/socket.io'.
  socketBaseUrl?: string;
  socketPath?: string;
  // Called on 401/session-expired instead of navigating to AP's /sign-in.
  onUnauthorized?: (reason: string) => void;
  // Storage backing token/projectId (default window.localStorage; a host may pass
  // an isolated store so multiple embeds don't collide).
  storage?: Storage;
  // Lock the canvas to a single flow (hide flow-switching navigation).
  disableNavigation?: boolean;
  // Extra EmbeddingState switch overrides.
  embedding?: Record<string, unknown>;
};
