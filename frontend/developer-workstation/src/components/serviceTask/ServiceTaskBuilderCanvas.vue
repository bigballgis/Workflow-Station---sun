<!--
  HERMES L1: DW (Vue 3) host wrapper that mounts the vendored ServiceTask builder
  (React, lib-mode ESM bundle) inside a Shadow DOM — NOT an iframe (project X-6).

  Contract with the builder bundle (automation/packages/web, built via
  vite.embed.config.mts -> dist/packages/web-embed):
    - `ap-builder.mjs` exports `mountApBuilder(config) => unmount`.
    - `web.css` is the Tailwind v4 stylesheet; it is injected as an inline <style>
      INTO the shadow root (AG-04.4 — must never reach document.head or it pollutes DW).
  The React app runs entirely inside the shadow root: styles are isolated both ways,
  and ApRouter uses its in-memory router (isEmbedded) so the DW URL is untouched.

  Auth/routing (L7 + L2): the host passes a per-user AP token (minted by the :8085
  bridge managed-authn exchange) and points REST/socket.io at the Kong gateway prefix
  (`/api/ap`, `/api/ap/socket.io`). AP owns auth; DW only supplies the token + config.

  `bundleUrl` / `cssUrl` are where the host serves the web-embed artifacts (delivery
  method per AG-02.8 — build-time copy into DW public/, local package, or a private
  offline mirror; must be air-gap capable, X-3).
-->
<template>
  <div
    ref="hostRef"
    class="ap-builder-canvas"
  />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';

type MountBuilderConfig = {
  container: HTMLElement | DocumentFragment;
  flowId: string;
  projectId: string;
  token: string;
  apiUrl: string;
  socketBaseUrl?: string;
  socketPath?: string;
  onUnauthorized?: (reason: string) => void;
  disableNavigation?: boolean;
};
type ApBuilderModule = {
  mountApBuilder: (config: MountBuilderConfig) => () => void;
};

const props = withDefaults(
  defineProps<{
    flowId: string;
    projectId: string;
    /** Per-user AP token from the L7 bridge (managed-authn exchange). */
    token: string;
    /** Kong REST prefix, e.g. `${origin}/api/ap`. */
    apiUrl: string;
    socketBaseUrl?: string;
    /** Kong socket.io path, e.g. '/api/ap/socket.io'. */
    socketPath?: string;
    /** URL of the built ESM entry (ap-builder.mjs). */
    bundleUrl: string;
    /** URL of the built stylesheet (web.css), injected inline into the shadow root. */
    cssUrl: string;
    disableNavigation?: boolean;
  }>(),
  {
    socketBaseUrl: undefined,
    socketPath: '/api/ap/socket.io',
    disableNavigation: true,
  },
);

const emit = defineEmits<{ (e: 'unauthorized', reason: string): void }>();

const hostRef = ref<HTMLElement | null>(null);
let unmount: (() => void) | null = null;

onMounted(async () => {
  const host = hostRef.value;
  if (!host) {
    return;
  }
  const shadow = host.attachShadow({ mode: 'open' });

  // AG-04.4: Tailwind CSS goes INTO the shadow root, never document.head.
  const css = await fetch(props.cssUrl).then((r) => r.text());
  const style = document.createElement('style');
  style.textContent = adaptCssForShadowRoot(css);
  shadow.appendChild(style);

  const container = document.createElement('div');
  container.style.height = '100%';
  container.style.width = '100%';
  // Size container so the 100cqh rewrite in adaptCssForShadowRoot() resolves
  // against THIS element (cqh pierces the builder's class-less height:auto
  // wrappers, which break a plain % chain).
  container.style.containerType = 'size';
  shadow.appendChild(container);

  const mod: ApBuilderModule = await import(/* @vite-ignore */ props.bundleUrl);
  unmount = mod.mountApBuilder({
    container,
    flowId: props.flowId,
    projectId: props.projectId,
    token: props.token,
    apiUrl: props.apiUrl,
    socketBaseUrl: props.socketBaseUrl,
    socketPath: props.socketPath,
    disableNavigation: props.disableNavigation,
    onUnauthorized: (reason: string) => emit('unauthorized', reason),
  });
});

onBeforeUnmount(() => {
  unmount?.();
  unmount = null;
});

/**
 * Three shadow-root adaptations the stock stylesheet needs (all no-ops outside
 * a shadow tree, which is why upstream never hits them):
 *
 * 1. `:root` → `:host`. AP declares its theme variables (--background/--border/…)
 *    on `:root`, which never matches inside a shadow root — every bg-background/
 *    border-border utility would resolve to nothing.
 *
 * 2. Chromium ignores `@property` registrations found in shadow-root stylesheets,
 *    so Tailwind v4 utilities that read registered vars (`border-style:
 *    var(--tw-border-style)`, shadows, transforms…) resolve to guaranteed-invalid
 *    — inputs and cards lose their borders. Re-emit each registered initial value
 *    as a universal-selector rule appended to the low-priority `properties` layer:
 *    the exact fallback Tailwind ships for browsers without @property support.
 *
 * 3. `100svh/100dvh/100vh` → `100cqh`. Viewport units track the browser window,
 *    not the embed container, so the builder's `h-svh` SidebarProvider wrapper
 *    overflows the canvas section: the step-settings panel believes the clipped
 *    area is visible and its ScrollArea stops scrolling. cqh resolves against
 *    the mount container (container-type: size above).
 */
function adaptCssForShadowRoot(css: string): string {
  const hostCss = css
    .replace(/:root\b/g, ':host')
    .replace(/\b100(?:s|d)?vh\b/g, '100cqh');

  const fallbackDecls: string[] = [];
  const propertyRulePattern = /@property\s+(--[\w-]+)\s*\{([^}]*)\}/g;
  let match: RegExpExecArray | null;
  while ((match = propertyRulePattern.exec(hostCss)) !== null) {
    const initialValue = match[2].match(/initial-value\s*:\s*([^;]+);?/)?.[1];
    if (initialValue !== undefined) {
      fallbackDecls.push(`${match[1]}: ${initialValue.trim()};`);
    }
  }
  if (fallbackDecls.length === 0) {
    return hostCss;
  }
  return (
    `${hostCss}\n` +
    `@layer properties { *, ::before, ::after, ::backdrop { ${fallbackDecls.join(' ')} } }`
  );
}
</script>

<style scoped>
.ap-builder-canvas {
  height: 100%;
  width: 100%;
}
</style>
