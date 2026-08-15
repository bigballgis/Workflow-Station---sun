import { rmSync } from 'fs';
// HERMES L1: lib-mode build that packages the AP builder as an embeddable ESM
// bundle for the Developer Workstation host. Mirrors vite.config.mts's resolve
// (alias + codemirror dedupe) and the React/Tailwind plugins, but drops the
// index.html plugin and the dev type-checker, and emits a single library entry
// (src/embed/mount-builder.tsx) instead of an app. React is bundled in (the host
// is Vue, so there is nothing to dedupe against on its side).
import path from 'path';

import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';
import tsconfigPaths from 'vite-tsconfig-paths';
// HERMES-PATCH-009: same rewrite for the DW-embedded builder bundle.
import apCdnRewritePlugin from './vite-plugins/ap-cdn-rewrite';


// HERMES: publicDir assets are copied verbatim into the embed output, but `ap-cdn/`
// (4.2MB of mirrored piece icons + brand assets) must NOT ride along: the host page
// reaches those through its own `/ap-cdn/` proxy to the AP web app (see the DW nginx
// `^~ /ap-cdn/` location), and shipping a second copy inside the DW bundle would blow
// the NFR-1 budget for no benefit. Everything else public/ carries (locales/, fonts/,
// piece-icons/) IS needed relative to the bundle URL, so only ap-cdn is dropped.
const dropApCdnFromEmbedPlugin = () => ({
  name: 'hermes-drop-ap-cdn-from-embed',
  closeBundle() {
    const dir = path.resolve(__dirname, '../../dist/packages/web-embed/ap-cdn');
    rmSync(dir, { recursive: true, force: true });
  },
});

export default defineConfig({
  root: __dirname,
  cacheDir: '../../node_modules/.vite/packages/web-embed',
  resolve: {
    dedupe: [
      '@codemirror/state',
      '@codemirror/view',
      '@codemirror/language',
      '@codemirror/commands',
      'react',
      'react-dom',
    ],
    // Keep in sync with vite.config.mts (0.88 split @activepieces/shared into
    // core/shared + core/utils + core/formula + core/piece-types + core/execution).
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@activepieces/shared': path.resolve(
        __dirname,
        '../../packages/core/shared/src',
      ),
      '@activepieces/pieces-framework': path.resolve(
        __dirname,
        '../../packages/pieces/framework/src',
      ),
      '@activepieces/core-utils': path.resolve(
        __dirname,
        '../../packages/core/utils/src',
      ),
      '@activepieces/core-formula': path.resolve(
        __dirname,
        '../../packages/core/formula/src',
      ),
      '@activepieces/core-piece-types': path.resolve(
        __dirname,
        '../../packages/core/piece-types/src',
      ),
      '@activepieces/core-execution': path.resolve(
        __dirname,
        '../../packages/core/execution/src',
      ),
    },
  },
  plugins: [apCdnRewritePlugin(), react(), tailwindcss(), tsconfigPaths(), dropApCdnFromEmbedPlugin()],
  define: {
    'process.env.NODE_ENV': JSON.stringify('production'),
    // Read by src/i18n.ts to load translations relative to the bundle URL
    // instead of the host origin's root (the host serves the bundle + locales
    // under its own base path, e.g. /dev/service-task-builder/).
    'import.meta.env.AP_EMBED_BUILD': 'true',
  },
  build: {
    outDir: '../../dist/packages/web-embed',
    emptyOutDir: true,
    sourcemap: false,
    cssCodeSplit: false,
    lib: {
      entry: path.resolve(__dirname, './src/embed/mount-builder.tsx'),
      name: 'ApBuilder',
      formats: ['es'],
      fileName: () => 'ap-builder.mjs',
    },
  },
});
