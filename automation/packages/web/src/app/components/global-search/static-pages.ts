// HERMES FR-D2: templates / impact / platform-admin pages are removed from this
// build, so global search only offers the surviving static destinations.
import { type ComponentType } from 'react';

import { WorkflowIcon } from '@/components/icons/workflow';

export type StaticPage = {
  id: string;
  label: string;
  href: string;
  icon: ComponentType<{ className?: string; size?: number }>;
  requiresPlatformAdmin?: boolean;
};

export const STATIC_PAGES: StaticPage[] = [
  {
    id: 'page-automations',
    label: 'Automations',
    href: '/automations',
    icon: WorkflowIcon,
  },
];
