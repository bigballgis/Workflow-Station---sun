import { TelemetryEvent } from '@activepieces/shared';
import React from 'react';

/**
 * HERMES-PATCH-024: product telemetry removed.
 *
 * Upstream initialised PostHog here with a HARDCODED upstream project key
 * (`phc_7F92…`), `ui_host: 'https://us.posthog.com'` and
 * `cross_subdomain_cookie: true` — the last one deliberately adopts the identity
 * cookie set by Activepieces' own marketing site on `.activepieces.com`. None of
 * that has any place in an air-gapped, self-hosted fork (X-3): the host is
 * unreachable, and shipping someone else's analytics key plus a cross-subdomain
 * identity cookie into our bundle is a privacy surface we get nothing back from.
 *
 * It was already inert in practice — doubly gated by the TELEMETRY_ENABLED flag
 * (server default flipped to fail-closed by HERMES-PATCH-020) and by the embed
 * check (the DW-hosted builder always sets `isEmbedded`). "Inert" is not the same
 * as "absent" though: the key, the host and the session-recording sampler were all
 * still in the shipped JavaScript, one flag flip away from live.
 *
 * The context is kept as a no-op so the ~10 `useTelemetry()` call sites keep
 * compiling and stay ready for a self-hosted sink later. Capture calls are dropped
 * on the floor deliberately — silently, because a telemetry no-op is exactly the
 * place where an error toast would be noise, and nothing downstream reads a result.
 */
interface TelemetryProviderProps {
  children: React.ReactNode;
}

interface TelemetryContextType {
  capture: (event: TelemetryEvent) => void;
  reset: () => void;
}

const noopTelemetry: TelemetryContextType = {
  capture: () => {
    /* no sink: product telemetry is removed (HERMES-PATCH-024) */
  },
  reset: () => {
    /* no sink */
  },
};

const TelemetryContext =
  React.createContext<TelemetryContextType>(noopTelemetry);

export const useTelemetry = () => React.useContext(TelemetryContext);

const TelemetryProvider = ({ children }: TelemetryProviderProps) => {
  return (
    <TelemetryContext.Provider value={noopTelemetry}>
      {children}
    </TelemetryContext.Provider>
  );
};

export default TelemetryProvider;
