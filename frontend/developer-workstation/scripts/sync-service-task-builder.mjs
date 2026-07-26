#!/usr/bin/env node
/**
 * Copy the built ServiceTask (Activepieces) builder bundle into DW's public/ so Vite
 * emits it into dist/ and DW's own nginx serves it — the "build-time copy" delivery
 * option from AG-02.8. No registry and no network at runtime, so it stays air-gap
 * capable (X-3).
 *
 * Source:  activepieces/dist/packages/web-embed   (built by
 *          `npx vite build --config vite.embed.config.mts` in activepieces/packages/web)
 * Target:  public/service-task-builder            (gitignored — a build artifact)
 *
 * Runs as a `prebuild` hook and is deliberately tolerant: if the bundle has not been
 * built, it warns and exits 0 so a plain `npm run build` still works (the Service Task
 * tab then reports the builder assets are unavailable instead of breaking the build).
 */
import { cp, rm, stat, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const SRC = resolve(here, '../../../activepieces/dist/packages/web-embed');
const DEST = resolve(here, '../public/service-task-builder');

const exists = async (p) => {
  try {
    await stat(p);
    return true;
  } catch {
    return false;
  }
};

if (!(await exists(SRC))) {
  console.warn(
    `[sync-service-task-builder] SKIP — bundle not found at ${SRC}\n` +
      '  Build it first: cd activepieces/packages/web && ' +
      'npx vite build --config vite.embed.config.mts',
  );
  process.exit(0);
}

await rm(DEST, { recursive: true, force: true });
await mkdir(dirname(DEST), { recursive: true });
await cp(SRC, DEST, { recursive: true });
console.log(`[sync-service-task-builder] copied ${SRC} -> ${DEST}`);
