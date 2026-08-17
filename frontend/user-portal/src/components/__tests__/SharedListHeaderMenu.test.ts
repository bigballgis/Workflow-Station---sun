import { describe, expect, it } from 'vitest'
import {
  isCompleteFilter,
  operatorLabelKey,
  operatorNeedsRange,
  operatorNeedsValue,
  type ListColumnMeta,
} from '@platform-shared/list/columnMeta'
import { listHeaderMenuItems } from '@platform-shared/list/listHeaderMenu'

function column(overrides: Partial<ListColumnMeta> = {}): ListColumnMeta {
  return {
    field: 'status',
    label: 'Status',
    kind: 'ENUM',
    filterable: true,
    sortable: true,
    groupable: true,
    operators: ['eq', 'ne', 'isNull', 'isNotNull'],
    ...overrides,
  }
}

describe('listHeaderMenuItems — declaration-driven menu', () => {
  it('groupable:false column has NO group entry at all', () => {
    const items = listHeaderMenuItems(column({ kind: 'TEXT', groupable: false }), {})
    expect(items.map((i) => i.command)).toEqual(['sortAsc', 'sortDesc', 'filter'])
  })

  it('groupable column toggles between groupBy and ungroup labels', () => {
    const grouped = listHeaderMenuItems(column(), { grouped: true })
    expect(grouped.find((i) => i.command === 'group')?.labelKey).toBe('sharedList.ungroup')
    const ungrouped = listHeaderMenuItems(column(), { grouped: false })
    expect(ungrouped.find((i) => i.command === 'group')?.labelKey).toBe('sharedList.groupBy')
  })

  it('sortable:false column has no sort entries; DATETIME uses older/newer labels', () => {
    expect(
      listHeaderMenuItems(column({ sortable: false }), {}).map((i) => i.command),
    ).toEqual(['group', 'filter'])

    const dateItems = listHeaderMenuItems(column({ kind: 'DATETIME', groupable: false }), {})
    expect(dateItems.find((i) => i.command === 'sortAsc')?.labelKey).toBe('sharedList.sortOlder')
    expect(dateItems.find((i) => i.command === 'sortDesc')?.labelKey).toBe('sharedList.sortNewer')
  })

  it('active sort adds clearSort; active filter adds clearFilter and the dot marker', () => {
    const items = listHeaderMenuItems(column(), { sort: 'DESC', filtered: true })
    const commands = items.map((i) => i.command)
    expect(commands).toContain('clearSort')
    expect(commands).toContain('clearFilter')
    expect(items.find((i) => i.command === 'filter')?.activeDot).toBe(true)
  })

  it('move entries appear only with showMove and honor boundary disabling', () => {
    expect(listHeaderMenuItems(column(), {}).some((i) => i.command === 'moveLeft')).toBe(false)
    const items = listHeaderMenuItems(column(), {
      showMove: true,
      canMoveLeft: false,
      canMoveRight: true,
    })
    expect(items.find((i) => i.command === 'moveLeft')?.disabled).toBe(true)
    expect(items.find((i) => i.command === 'moveRight')?.disabled).toBe(false)
  })

  it('non-filterable, non-sortable, non-groupable column yields an empty menu', () => {
    expect(
      listHeaderMenuItems(
        column({ filterable: false, sortable: false, groupable: false }),
        {},
      ),
    ).toEqual([])
  })
})

describe('columnMeta operator helpers', () => {
  it('isNull/isNotNull need no value; between needs both bounds', () => {
    expect(operatorNeedsValue('isNull')).toBe(false)
    expect(operatorNeedsValue('contains')).toBe(true)
    expect(operatorNeedsRange('between')).toBe(true)
    expect(operatorNeedsRange('eq')).toBe(false)

    expect(isCompleteFilter({ operator: 'isNull', value: '' })).toBe(true)
    expect(isCompleteFilter({ operator: 'eq', value: '' })).toBe(false)
    expect(isCompleteFilter({ operator: 'eq', value: 'x' })).toBe(true)
    expect(isCompleteFilter({ operator: 'between', value: '1', value2: '' })).toBe(false)
    expect(isCompleteFilter({ operator: 'between', value: '1', value2: '9' })).toBe(true)
  })

  it('operatorLabelKey throws on operators outside the shared map (no blank menu entries)', () => {
    expect(operatorLabelKey('gte')).toBe('sharedList.opGte')
    expect(() => operatorLabelKey('regexMatch')).toThrow(/out of sync/)
  })
})
