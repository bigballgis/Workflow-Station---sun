/**
 * @vitest-environment jsdom
 *
 * The formula editor's "See All" docs link is driven entirely by `docsUrl`:
 * embedders who set `embedding.formulasDocsUrl` get their own reference, and
 * everyone else gets no link at all (HERMES-PATCH-023 removed the upstream-docs
 * fallback). Without this, a user could be silently sent to unreachable docs.
 *
 * This file uses raw `react-dom` + React's `act` rather than
 * @testing-library/react (which is not a dependency of this package), so the
 * testing-library lint rules that assume that library do not apply.
 */
/* eslint-disable testing-library/no-unnecessary-act */
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, describe, expect, it } from 'vitest';

import { FunctionSearchPopover } from '@/app/builder/piece-properties/text-input-with-mentions/components/function-search-popover';

declare global {
  // Tells React the test wraps updates in act(); see React's act() docs.
  // eslint-disable-next-line no-var
  var IS_REACT_ACT_ENVIRONMENT: boolean;
}

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

let root: Root | null = null;
let container: HTMLDivElement | null = null;

function renderPopover({ docsUrl }: { docsUrl?: string } = {}) {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  act(() => {
    root?.render(
      <FunctionSearchPopover
        query=""
        position={{ top: 0, left: 0 }}
        editorRef={{ current: null }}
        onSelect={() => undefined}
        onClose={() => undefined}
        docsUrl={docsUrl}
      />,
    );
  });
}

function renderedDocsLink() {
  return document.body.querySelector<HTMLAnchorElement>('a[target="_blank"]');
}

afterEach(() => {
  act(() => root?.unmount());
  container?.remove();
  root = null;
  container = null;
});

describe('FunctionSearchPopover docs link', () => {
  it('renders no link when docsUrl is unset (embedded, formulasDocsUrl not configured)', () => {
    renderPopover();
    expect(renderedDocsLink()).toBeNull();
  });

  it('points at the embedder-supplied url when docsUrl is set', () => {
    renderPopover({ docsUrl: 'https://acme.test/help/formulas' });
    expect(renderedDocsLink()?.getAttribute('href')).toBe(
      'https://acme.test/help/formulas',
    );
  });

  // HERMES-PATCH-023: the old third case asserted a DEFAULT_FORMULAS_DOCS_URL
  // fallback to upstream docs. That constant is gone (dead link air-gapped, and
  // upstream's formula docs no longer describe this fork), so "no host url" now
  // means "no link" — which is exactly the first case above.
});
