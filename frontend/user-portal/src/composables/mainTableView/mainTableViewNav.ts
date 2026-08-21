export type NavView = { id: number; viewName?: string; isDefault?: boolean; tableId?: number | null; tableLabel?: string | null }

export type TableViewGroup = {
  tableId: number | null
  label: string
  views: NavView[]
}

export function tableGroupKey(group: Pick<TableViewGroup, 'tableId' | 'label'>): string {
  return String(group.tableId ?? group.label)
}

export function groupViewsByTable(views: NavView[]): TableViewGroup[] {
  const groups: TableViewGroup[] = []
  const byTable = new Map<string, TableViewGroup>()
  for (const v of views) {
    const label = v.tableLabel || v.viewName || ''
    const key = String(v.tableId ?? label)
    let g = byTable.get(key)
    if (!g) {
      g = { tableId: v.tableId ?? null, label, views: [] }
      byTable.set(key, g)
      groups.push(g)
    }
    g.views.push(v)
  }
  return groups
}

export function sortViewsByName(views: NavView[], locale?: string): NavView[] {
  return [...views].sort((a, b) =>
    (a.viewName || '').localeCompare(b.viewName || '', locale, { sensitivity: 'base' }),
  )
}

export function pickDefaultView(views: NavView[], locale?: string): NavView | undefined {
  const sorted = sortViewsByName(views, locale)
  return sorted.find(v => v.isDefault) ?? sorted[0]
}

export function filterTableGroups(groups: TableViewGroup[], keyword: string): TableViewGroup[] {
  const kw = keyword.trim().toLowerCase()
  if (!kw) return groups
  return groups.filter(g =>
    (g.label || '').toLowerCase().includes(kw)
    || g.views.some(v => (v.viewName || '').toLowerCase().includes(kw)),
  )
}
