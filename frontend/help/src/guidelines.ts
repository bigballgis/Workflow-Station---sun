import type { Component } from 'vue'

export interface Guideline {
  id: string
  path: string
  titleKey: string
  summaryKey: string
  load: () => Promise<{ default: Component }>
}

export interface NavLeaf {
  kind: 'leaf'
  id: string
  titleKey: string
  /** Article path, optional hash, e.g. /email-send#connection */
  to?: string
}

/** Leaf that already has a guideline `to` (home cards, not grey menus). */
export type NavLeafWithArticle = NavLeaf & { to: string }

export interface NavGroup {
  kind: 'group'
  id: string
  titleKey: string
  open?: boolean
  children: NavNode[]
}

export type NavNode = NavGroup | NavLeaf

/** Add a guideline: append here, then add the view + i18n keys + a NAV_TREE leaf. */
export const GUIDELINES: Guideline[] = [
  {
    id: 'computed-fields',
    path: '/computed-fields',
    titleKey: 'guides.computedFields.title',
    summaryKey: 'guides.computedFields.summary',
    load: () => import('@/views/ComputedFieldGuide.vue'),
  },
  {
    id: 'email-send',
    path: '/email-send',
    titleKey: 'guides.emailSend.title',
    summaryKey: 'guides.emailSend.summary',
    load: () => import('@/views/EmailSendGuide.vue'),
  },
  {
    id: 'email-monitor',
    path: '/email-monitor',
    titleKey: 'guides.emailMonitor.title',
    summaryKey: 'guides.emailMonitor.summary',
    load: () => import('@/views/EmailMonitorGuide.vue'),
  },
  {
    id: 'up-tasks-to-claim',
    path: '/up-tasks-to-claim',
    titleKey: 'guides.upTasksToClaim.title',
    summaryKey: 'guides.upTasksToClaim.summary',
    load: () => import('@/views/UpTasksToClaimGuide.vue'),
  },
]

/**
 * Mirrors the three product sidebars (and Function Unit designer tabs).
 * Leaves without `to` are menus that exist in the product but have no article yet.
 */
export const NAV_TREE: NavNode[] = [
  {
    kind: 'group',
    id: 'dw',
    titleKey: 'nav.dw',
    open: true,
    children: [
      {
        kind: 'group',
        id: 'dw-fu',
        titleKey: 'nav.functionUnits',
        open: true,
        children: [
          { kind: 'leaf', id: 'dw-process', titleKey: 'nav.processDesign' },
          {
            kind: 'leaf',
            id: 'dw-tables',
            titleKey: 'nav.tableDesign',
            to: '/computed-fields',
          },
          { kind: 'leaf', id: 'dw-forms', titleKey: 'nav.formDesign' },
          { kind: 'leaf', id: 'dw-view', titleKey: 'nav.viewDesign' },
          { kind: 'leaf', id: 'dw-action', titleKey: 'nav.actionDesign' },
          { kind: 'leaf', id: 'dw-fu-automation', titleKey: 'nav.fuAutomation' },
          {
            kind: 'leaf',
            id: 'dw-connections',
            titleKey: 'nav.connections',
            to: '/email-send#connection',
          },
          {
            kind: 'leaf',
            id: 'dw-email-templates',
            titleKey: 'nav.emailTemplates',
            to: '/email-send#template',
          },
          {
            kind: 'leaf',
            id: 'dw-email-monitors',
            titleKey: 'nav.emailMonitors',
            to: '/email-monitor',
          },
          { kind: 'leaf', id: 'dw-decisions', titleKey: 'nav.decisionDesign' },
          { kind: 'leaf', id: 'dw-versions', titleKey: 'nav.versionManagement' },
        ],
      },
      { kind: 'leaf', id: 'dw-automation', titleKey: 'nav.automation' },
    ],
  },
  {
    kind: 'group',
    id: 'admin',
    titleKey: 'nav.admin',
    children: [
      { kind: 'leaf', id: 'ac-dashboard', titleKey: 'nav.acDashboard' },
      { kind: 'leaf', id: 'ac-users', titleKey: 'nav.acUsers' },
      {
        kind: 'group',
        id: 'ac-entitlement',
        titleKey: 'nav.acEntitlement',
        children: [
          { kind: 'leaf', id: 'ac-org', titleKey: 'nav.acOrganization' },
          { kind: 'leaf', id: 'ac-vg', titleKey: 'nav.acVirtualGroup' },
          { kind: 'leaf', id: 'ac-role', titleKey: 'nav.acRoles' },
        ],
      },
      { kind: 'leaf', id: 'ac-fu', titleKey: 'nav.acFunctionUnit' },
      {
        kind: 'group',
        id: 'ac-bi',
        titleKey: 'nav.acBi',
        children: [
          { kind: 'leaf', id: 'ac-bi-reg', titleKey: 'nav.acBiRegistry' },
          { kind: 'leaf', id: 'ac-bi-assign', titleKey: 'nav.acBiAssignment' },
          { kind: 'leaf', id: 'ac-bi-rbac', titleKey: 'nav.acBiRbac' },
        ],
      },
      {
        kind: 'group',
        id: 'ac-audit',
        titleKey: 'nav.acAudit',
        children: [
          { kind: 'leaf', id: 'ac-audit-admin', titleKey: 'nav.acAuditAdmin' },
          { kind: 'leaf', id: 'ac-audit-portal', titleKey: 'nav.acAuditPortal' },
        ],
      },
      {
        kind: 'group',
        id: 'ac-rt',
        titleKey: 'nav.acRelationTables',
        children: [
          {
            kind: 'leaf',
            id: 'ac-rt-struct',
            titleKey: 'nav.acTableStructure',
            to: '/computed-fields#relation',
          },
          { kind: 'leaf', id: 'ac-rt-data', titleKey: 'nav.acTableData' },
        ],
      },
      { kind: 'leaf', id: 'ac-pieces', titleKey: 'nav.acPieces' },
      { kind: 'leaf', id: 'ac-flows', titleKey: 'nav.acFlowMigration' },
      { kind: 'leaf', id: 'ac-config', titleKey: 'nav.acSystemConfig' },
    ],
  },
  {
    kind: 'group',
    id: 'portal',
    titleKey: 'nav.portal',
    children: [
      { kind: 'leaf', id: 'up-home', titleKey: 'nav.upHome' },
      {
        kind: 'group',
        id: 'up-task',
        titleKey: 'nav.upSectionTask',
        children: [
          { kind: 'leaf', id: 'up-todo', titleKey: 'nav.upTodo' },
          {
            kind: 'leaf',
            id: 'up-to-claim',
            titleKey: 'nav.upTasksToClaim',
            to: '/up-tasks-to-claim',
          },
          { kind: 'leaf', id: 'up-done', titleKey: 'nav.upCompleted' },
        ],
      },
      {
        kind: 'group',
        id: 'up-request',
        titleKey: 'nav.upSectionRequest',
        children: [
          { kind: 'leaf', id: 'up-new', titleKey: 'nav.upNewRequests' },
          { kind: 'leaf', id: 'up-mine', titleKey: 'nav.upMyRequests' },
        ],
      },
      {
        kind: 'group',
        id: 'up-audit',
        titleKey: 'nav.upSectionAudit',
        children: [{ kind: 'leaf', id: 'up-all', titleKey: 'nav.upAllRequests' }],
      },
      {
        kind: 'group',
        id: 'up-data',
        titleKey: 'nav.upSectionData',
        children: [
          { kind: 'leaf', id: 'up-rt', titleKey: 'nav.upRelationTables' },
          { kind: 'leaf', id: 'up-views', titleKey: 'nav.upViews' },
        ],
      },
      {
        kind: 'group',
        id: 'up-setup',
        titleKey: 'nav.upSectionSetup',
        children: [
          { kind: 'leaf', id: 'up-deleg', titleKey: 'nav.upDelegations' },
          { kind: 'leaf', id: 'up-profile', titleKey: 'nav.upProfileSetup' },
        ],
      },
    ],
  },
]

export function guideByPath(path: string): Guideline | undefined {
  return GUIDELINES.find((g) => g.path === path)
}

export function navLeavesWithArticles(nodes: NavNode[] = NAV_TREE): NavLeafWithArticle[] {
  const out: NavLeafWithArticle[] = []
  for (const node of nodes) {
    if (node.kind === 'leaf') {
      if (node.to) out.push({ ...node, to: node.to })
    } else {
      out.push(...navLeavesWithArticles(node.children))
    }
  }
  return out
}
