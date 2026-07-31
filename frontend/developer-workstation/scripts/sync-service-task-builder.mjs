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
 * Runs as a `prebuild` hook and is deliberately tolerant by default: if the bundle has
 * not been built, it warns and exits 0 so a plain `npm run build` still works (the
 * Service Task tab then reports the builder assets are unavailable instead of breaking
 * the build).
 *
 * That tolerance is wrong for a release build, where the result is an image that only
 * fails in the browser (404 on /dev/service-task-builder/web.css). Set
 * SERVICE_TASK_BUILDER_REQUIRED=1 — deploy/scripts/build-and-push-k8s.ps1 does — to turn
 * the missing bundle into a hard failure at build time instead.
 *
 * The opposite intent — a release that deliberately leaves Activepieces out — sets
 * SERVICE_TASK_BUILDER_SKIP=1 (build-and-push-k8s.ps1 -NoServiceTaskBuilder). Not copying
 * is not enough there: a bundle left in public/ by an earlier normal build would be picked
 * up by Vite regardless, so this also REMOVES the destination. public/service-task-builder
 * is a gitignored copy — the source in activepieces/dist/ is untouched and the next normal
 * build recreates it.
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

// Checked before SRC: the point is an image without the builder, so whether a bundle
// happens to be lying around is irrelevant — and SKIP wins over REQUIRED rather than
// tripping over a leftover variable from an earlier run in the same shell.
if (process.env.SERVICE_TASK_BUILDER_SKIP) {
  const had = await exists(DEST);
  await rm(DEST, { recursive: true, force: true });
  console.warn(
    `[sync-service-task-builder] SKIP (SERVICE_TASK_BUILDER_SKIP) — not copying the ` +
      `Activepieces builder bundle${had ? `; removed the stale copy at ${DEST}` : ''}.\n` +
      '  This image ships WITHOUT the Automation builder; the tab reports it as unavailable.',
  );
  process.exit(0);
}

if (!(await exists(SRC))) {
  const howToBuild =
    '  Build it first: cd activepieces/packages/web && ' +
    'npx vite build --config vite.embed.config.mts';
  if (process.env.SERVICE_TASK_BUILDER_REQUIRED) {
    console.error(
      `[sync-service-task-builder] MISSING — bundle not found at ${SRC}\n` +
        `${howToBuild}\n` +
        '  SERVICE_TASK_BUILDER_REQUIRED is set, so this is a hard failure: continuing\n' +
        '  would ship an image whose Service Task tab 404s on web.css at runtime.',
    );
    process.exit(1);
  }
  console.warn(
    `[sync-service-task-builder] SKIP — bundle not found at ${SRC}\n${howToBuild}`,
  );
  process.exit(0);
}

await rm(DEST, { recursive: true, force: true });
await mkdir(dirname(DEST), { recursive: true });
await cp(SRC, DEST, { recursive: true });
console.log(`[sync-service-task-builder] copied ${SRC} -> ${DEST}`);
