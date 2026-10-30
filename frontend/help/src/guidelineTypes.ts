import type { Component } from 'vue'

export interface Guideline {
  id: string
  path: string
  titleKey: string
  summaryKey: string
  load: () => Promise<{ default: Component }>
  /** Skip the home “by job” grid (per-control articles). */
  omitFromHome?: boolean
}

export interface NavLeaf {
  kind: 'leaf'
  id: string
  titleKey: string
  /** Article path, optional hash, e.g. /email-send#connection */
  to?: string
  /** Sidebar only — skip the home card grid (per-control leaves). */
  omitFromHome?: boolean
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
