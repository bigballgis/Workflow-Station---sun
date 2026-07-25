// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { apHost } from '@/lib/host-config';

const KEY = '__AP_HOST_CONFIG__';

// HERMES L1: proves the builder-embedding injection points read host config
// lazily and fall back to standalone AP behaviour. This is the runtime contract
// the DW host relies on (API base -> Kong /api/ap, socket path, etc.).
describe('apHost injection (L1 builder embedding)', () => {
  afterEach(() => {
    delete (window as unknown as Record<string, unknown>)[KEY];
  });

  it('falls back to standalone same-origin /api when no host config', () => {
    expect(apHost.getConfig()).toEqual({});
    expect(apHost.getApiUrl()).toBe(`${window.location.origin}/api`);
    expect(apHost.getSocketPath()).toBe('/api/socket.io');
    expect(apHost.getSocketBaseUrl()).toBe(window.location.origin);
  });

  it('routes REST + socket.io at the host gateway prefix (L2 /api/ap)', () => {
    (window as unknown as Record<string, unknown>)[KEY] = {
      apiUrl: `${window.location.origin}/api/ap`,
      socketPath: '/api/ap/socket.io',
    };
    expect(apHost.getApiUrl()).toBe(`${window.location.origin}/api/ap`);
    expect(apHost.getSocketPath()).toBe('/api/ap/socket.io');
  });

  it('reads config lazily — config set after module load still applies', () => {
    expect(apHost.getApiUrl()).toBe(`${window.location.origin}/api`);
    (window as unknown as Record<string, unknown>)[KEY] = {
      apiUrl: 'https://dw.example/api/ap',
      socketBaseUrl: 'https://dw.example',
    };
    expect(apHost.getApiUrl()).toBe('https://dw.example/api/ap');
    expect(apHost.getSocketBaseUrl()).toBe('https://dw.example');
  });
});
