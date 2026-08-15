import { PageTitle } from '@/app/components/page-title';

import NotFoundPage from './404-page';
import AuthenticatePage from './authenticate';
import { RedirectPage } from './redirect';

// HERMES FR-D2: the upstream iframe embed routes (/embed*) are removed — X-6
// rejected the postMessage iframe SDK; DW mounts the builder via the lib-mode
// mount-builder entry instead. Public chat / forms / templates / mcp-authorize
// pages are removed with their feature domains.
export const publicRoutes = [
  {
    path: '/authenticate',
    element: <AuthenticatePage />,
  },
  {
    path: '/redirect',
    element: <RedirectPage></RedirectPage>,
  },
  {
    path: '/404',
    element: (
      <PageTitle title="Not Found">
        <NotFoundPage />
      </PageTitle>
    ),
  },
];
