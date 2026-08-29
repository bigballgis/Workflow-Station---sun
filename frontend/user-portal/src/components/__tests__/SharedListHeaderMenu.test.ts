import { describe, expect, it } from 'vitest'
import {
  isCompleteFilter,
  operatorLabelKey,
  operatorNeedsRange,
  operatorNeedsValue,
  type ListColumnMeta,
} from '@platform-shared/list/columnMeta'
import { listHeaderMenuItems } from '@platform-shared/list/listHeaderMenu'
import en from '@/i18n/locales/en'
import zhCN from '@/i18n/locales/zh-CN'
import zhTW from '@/i18n/locales/zh-TW'

function column(overrides: Partial<ListColumnMeta> = {}): ListColumnMeta {
  return {
    field: 'status',
    label: 'Status',
    kind: 'ENUM',
    filterable: true,
    sortable: true,
    operators: ['eq', 'ne', 'isNull', 'isNotNull'],
    ...overrides,
  }
}

describe('listHeaderMenuItems — declaration-driven menu', () => {
  it('TEXT column has sort and filter, never a group entry', () => {
    const items = listHeaderMenuItems(column({ kind: 'TEXT' }), {})
    expect(items.map((i) => i.command)).toEqual(['sortAsc', 'sortDesc', 'filter'])
    expect(items.some((i) => i.command === 'group')).toBe(false)
  })

  it.each(['ENUM', 'USER', 'BOOLEAN', 'NUMBER', 'DATETIME'] as const)(
    '%s column never emits a group command',
    (kind) => {
      const items = listHeaderMenuItems(column({ kind }), {
        sort: 'ASC',
        filtered: true,
        showMove: true,
        canMoveLeft: true,
        canMoveRight: true,
      })
      const commands = items.map((i) => i.command)
      expect(commands).not.toContain('group')
      expect(commands).toEqual([
        'sortAsc',
        'sortDesc',
        'clearSort',
        'filter',
        'clearFilter',
        'moveLeft',
        'moveRight',
      ])
    },
  )

  it('sortable:false column has no sort entries; DATETIME uses older/newer labels', () => {
    expect(
      listHeaderMenuItems(column({ sortable: false }), {}).map((i) => i.command),
    ).toEqual(['filter'])

    const dateItems = listHeaderMenuItems(column({ kind: 'DATETIME' }), {})
    expect(dateItems.find((i) => i.command === 'sortAsc')?.labelKey).toBe('sharedList.sortOlder')
    expect(dateItems.find((i) => i.command === 'sortDesc')?.labelKey).toBe('sharedList.sortNewer')

    const numberItems = listHeaderMenuItems(column({ kind: 'NUMBER' }), {})
    expect(numberItems.find((i) => i.command === 'sortAsc')?.labelKey).toBe('sharedList.sortSmallToLarge')
    expect(numberItems.find((i) => i.command === 'sortDesc')?.labelKey).toBe('sharedList.sortLargeToSmall')
  })

  it('active sort adds clearSort; active filter adds clearFilter and the dot marker', () => {
    const items = listHeaderMenuItems(column(), { sort: 'DESC', filtered: true })
    const commands = items.map((i) => i.command)
    expect(commands).toContain('clearSort')
    expect(commands).toContain('clearFilter')
    expect(items.find((i) => i.command === 'filter')?.activeDot).toBe(true)
  })

  it('has no column-width command (resize is header-edge drag only)', () => {
    const commands = listHeaderMenuItems(column(), { showMove: true }).map((i) => i.command) as string[]
    expect(commands).not.toContain('columnWidth')
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

  it('non-filterable, non-sortable column yields an empty menu', () => {
    expect(
      listHeaderMenuItems(
        column({ filterable: false, sortable: false }),
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
    expect(operatorLabelKey('today')).toBe('sharedList.opToday')
    expect(() => operatorLabelKey('regexMatch')).toThrow(/out of sync/)
  })
})

describe('sharedList i18n — no header Group copy', () => {
  it.each([
    ['en', en.sharedList],
    ['zh-CN', zhCN.sharedList],
    ['zh-TW', zhTW.sharedList],
  ] as const)('%s has no groupBy / ungroup keys', (_locale, sharedList) => {
    expect(sharedList).not.toHaveProperty('groupBy')
    expect(sharedList).not.toHaveProperty('ungroup')
  })
})
