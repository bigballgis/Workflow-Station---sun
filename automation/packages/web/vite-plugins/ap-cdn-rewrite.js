/**
 * HERMES-PATCH-009 (docs/ap-integration/HERMES_PATCHES.md)
 *
 * Rewrites hardcoded `https://cdn.activepieces.com` references to the local mirror
 * served from our own origin (`/ap-cdn`).
 *
 * Production is air-gapped (DECISIONS X-2/X-3), so every upstream CDN reference —
 * core step icons, AI provider logos, auth backgrounds — renders broken there.
 * Upstream hardcodes the host in ~47 places across 16 files; rewriting at build time
 * keeps the vendored source diff-clean against upstream (Q8 frozen baseline) and
 * automatically covers references introduced by future merges.
 *
 * The mirror is populated by `deploy/pieces/mirror-ap-cdn.mjs` into
 * `packages/web/public/ap-cdn/`, preserving the CDN's own path layout.
 */
const CDN_ORIGIN = 'https://cdn.activepieces.com';
const LOCAL_MOUNT = '/ap-cdn';

export default function apCdnRewritePlugin() {
    return {
        name: 'hermes-ap-cdn-rewrite',
        enforce: 'pre',
        transform(code, id) {
            if (!/\.(ts|tsx|js|jsx)$/.test(id) || id.includes('node_modules')) {
                return null;
            }
            if (!code.includes(CDN_ORIGIN)) {
                return null;
            }
            return { code: code.split(CDN_ORIGIN).join(LOCAL_MOUNT), map: null };
        },
        transformIndexHtml(html) {
            return html.split(CDN_ORIGIN).join(LOCAL_MOUNT);
        },
    };
}
