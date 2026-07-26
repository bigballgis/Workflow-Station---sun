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
import apCdnRewritePlugin from './vite-plugins/ap-cdn-rewrite';

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
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@activepieces/shared': path.resolve(
        __dirname,
        '../../packages/shared/src',
      ),
      'ee-embed-sdk': path.resolve(__dirname, '../../packages/ee/embed-sdk/src'),
      '@activepieces/pieces-framework': path.resolve(
        __dirname,
        '../../packages/pieces/framework/src',
      ),
    },
  },
  plugins: [apCdnRewritePlugin(), react(), tailwindcss(), tsconfigPaths()],
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
