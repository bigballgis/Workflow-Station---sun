export type NavView = {
  id: number
  viewName?: string
  isDefault?: boolean
  tableId?: number | null
  tableLabel?: string | null
  /** Owning table type; 'MAIN' means rows open the request detail page. */
  tableType?: string | null
}

export type TableViewGroup = {
  tableId: number | null
  label: string
  /** Table type of the group's views — they all share one table, so it is single-valued. */
  tableType?: string | null
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
      g = { tableId: v.tableId ?? null, label, tableType: v.tableType ?? null, views: [] }
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

/**
 * Whether a view's rows are requests. A MAIN-table view has one row per process instance, so its
 * rows open the request detail page rather than a form designed for the view.
 */
export function isMainTableView(view: { tableType?: string | null } | null | undefined): boolean {
  return String(view?.tableType ?? '').toUpperCase() === 'MAIN'
}

/**
 * Where a row click lands, decided from the view's metadata and the row's identifiers alone.
 *
 * `request` opens the process instance — only MAIN views, whose rows *are* requests. `detail` opens
 * the form the designer bound to the view. The rest are refusals, each naming what is missing:
 * a MAIN row with no instance (`noDetailPage`), a view with no bound form (`noDetailForm`), or a
 * row with no usable key (`rowNotAddressable`). A non-MAIN view without a bound form deliberately
 * does *not* fall back to the owning request — that is a different record (the whole application,
 * not this row), so the click is refused with the reason instead of silently changing subject.
 */
export type RowOpenTarget =
  | { kind: 'request'; processInstanceId: string }
  | { kind: 'detail'; viewId: number; rowKey: string }
  | { kind: 'refuse'; messageKey: 'noDetailPage' | 'noDetailForm' | 'rowNotAddressable' }

export function resolveRowOpenTarget(
  view: { tableType?: string | null; detailFormId?: number | null } | null | undefined,
  viewId: number | null | undefined,
  row: { processInstanceId?: string | null },
  rowKey: string | null,
): RowOpenTarget {
  if (isMainTableView(view)) {
    // Checked rather than assumed: MAIN rows are built from process instances today, but routing
    // to /applications/undefined on a future row shape would strand the user on a broken page.
    return row.processInstanceId
      ? { kind: 'request', processInstanceId: String(row.processInstanceId) }
      : { kind: 'refuse', messageKey: 'noDetailPage' }
  }
  if (!view?.detailFormId || !viewId) return { kind: 'refuse', messageKey: 'noDetailForm' }
  if (!rowKey) return { kind: 'refuse', messageKey: 'rowNotAddressable' }
  return { kind: 'detail', viewId, rowKey }
}

export function filterTableGroups(groups: TableViewGroup[], keyword: string): TableViewGroup[] {
  const kw = keyword.trim().toLowerCase()
  if (!kw) return groups
  return groups.filter(g =>
    (g.label || '').toLowerCase().includes(kw)
    || g.views.some(v => (v.viewName || '').toLowerCase().includes(kw)),
  )
}
