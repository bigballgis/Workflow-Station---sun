import type { ListColumnMeta } from './columnMeta'

export type ListHeaderCommand =
  | 'sortAsc'
  | 'sortDesc'
  | 'clearSort'
  | 'filter'
  | 'clearFilter'
  | 'moveLeft'
  | 'moveRight'

export interface ListHeaderState {
  sort?: 'ASC' | 'DESC' | null
  filtered?: boolean
  showMove?: boolean
  canMoveLeft?: boolean
  canMoveRight?: boolean
}

export interface ListHeaderMenuItem {
  command: ListHeaderCommand
  labelKey: string
  divided?: boolean
  disabled?: boolean
  /** Render the "active" dot marker (currently-filtered indicator). */
  activeDot?: boolean
}

/**
 * Menu entries are driven by the column declaration: a non-sortable column gets no
 * sort entries. DATETIME columns label their sort directions older/newer, NUMBER
 * small-to-large, and everything else A→Z — the SQL already sorts that way
 * (`ListFilterSql.sortExpression`); the menu must not say A→Z on a numeric column.
 */
export function sortLabelKeys(kind: ListColumnMeta['kind']): { asc: string; desc: string } {
  if (kind === 'DATETIME') {
    return { asc: 'sharedList.sortOlder', desc: 'sharedList.sortNewer' }
  }
  if (kind === 'NUMBER') {
    return { asc: 'sharedList.sortSmallToLarge', desc: 'sharedList.sortLargeToSmall' }
  }
  return { asc: 'sharedList.sortAsc', desc: 'sharedList.sortDesc' }
}

export function listHeaderMenuItems(
  column: ListColumnMeta,
  state: ListHeaderState,
): ListHeaderMenuItem[] {
  const items: ListHeaderMenuItem[] = []
  const sortLabels = sortLabelKeys(column.kind)

  if (column.sortable) {
    items.push({
      command: 'sortAsc',
      labelKey: sortLabels.asc,
    })
    items.push({
      command: 'sortDesc',
      labelKey: sortLabels.desc,
    })
    if (state.sort) {
      items.push({ command: 'clearSort', labelKey: 'sharedList.clearSort' })
    }
  }

  if (column.filterable) {
    items.push({
      command: 'filter',
      labelKey: 'sharedList.filterBy',
      divided: items.length > 0,
      activeDot: state.filtered === true,
    })
    if (state.filtered) {
      items.push({ command: 'clearFilter', labelKey: 'sharedList.clearFilter' })
    }
  }

  if (state.showMove) {
    items.push({
      command: 'moveLeft',
      labelKey: 'sharedList.moveLeft',
      divided: items.length > 0,
      disabled: state.canMoveLeft === false,
    })
    items.push({
      command: 'moveRight',
      labelKey: 'sharedList.moveRight',
      disabled: state.canMoveRight === false,
    })
  }

  return items
}
