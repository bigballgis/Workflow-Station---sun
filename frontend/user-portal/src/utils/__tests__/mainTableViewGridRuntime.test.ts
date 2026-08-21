import { describe, expect, it } from 'vitest'
import {
  clampColumnWidth,
  createDefaultGridRuntime,
  insertGroupHeaders,
  loadGridRuntimeFromSession,
  moveColumn,
  saveGridRuntimeToSession,
  setColumnWidth,
  toListColumnMeta,
} from '../mainTableViewGridRuntime'

describe('mainTableViewGridRuntime', () => {
  const rows = [
    { rowKey: '1', processInstanceId: '1', values: { name: 'Beta', status: 'Open' } },
    { rowKey: '2', processInstanceId: '2', values: { name: 'Alpha', status: 'Open' } },
    { rowKey: '3', processInstanceId: '3', values: { name: 'Gamma', status: 'Closed' } },
  ]

  const groups = [
    { label: 'Open', count: 7 },
    { label: 'Closed', count: 4 },
  ]

  it('heads each run of rows with the count the server reported, not the count on this page', () => {
    const display = insertGroupHeaders(rows, 'status', groups)

    expect(display[0]).toMatchObject({ _isGroupHeader: true, _groupLabel: 'Open', _groupCount: 7 })
    expect(display[3]).toMatchObject({ _isGroupHeader: true, _groupLabel: 'Closed', _groupCount: 4 })
    expect(display).toHaveLength(5)
  })

  it('leaves rows untouched when nothing is grouped', () => {
    expect(insertGroupHeaders(rows, null, [])).toBe(rows)
  })

  it('refuses to render a group the server did not count', () => {
    expect(() => insertGroupHeaders(rows, 'status', [{ label: 'Open', count: 7 }]))
      .toThrow(/Closed/)
  })

  it('moveColumn swaps order', () => {
    const state = createDefaultGridRuntime()
    state.columnOrder = ['a', 'b', 'c']
    moveColumn(state, 'b', 'left')
    expect(state.columnOrder).toEqual(['b', 'a', 'c'])
  })

  it('toListColumnMeta copies closed options onto the shared header contract', () => {
    expect(
      toListColumnMeta({
        fieldName: 'process_status',
        displayLabel: 'Status',
        kind: 'ENUM',
        filterable: true,
        sortable: true,
        groupable: true,
        operators: ['eq', 'ne'],
        options: [
          { value: 'RUNNING', label: 'Running' },
          { value: 'COMPLETED', label: 'Completed' },
        ],
      }).options,
    ).toEqual([
      { value: 'RUNNING', label: 'Running' },
      { value: 'COMPLETED', label: 'Completed' },
    ])
  })

  it('clampColumnWidth enforces min and max', () => {
    expect(clampColumnWidth(30)).toBe(60)
    expect(clampColumnWidth(900)).toBe(600)
  })

  it('setColumnWidth stores clamped width', () => {
    const state = createDefaultGridRuntime()
    setColumnWidth(state, 'name', 180)
    expect(state.columnWidths.name).toBe(180)
  })

  it('remembers layout across a reload but never the query, which the server may since reject', () => {
    const state = createDefaultGridRuntime()
    state.columnOrder = ['name', 'status']
    state.columnWidths.name = 200
    state.sort = { fieldName: 'name', direction: 'ASC' }
    state.groupBy = 'status'
    state.filters.status = { operator: 'eq', value: 'Open' }

    saveGridRuntimeToSession(9, state)
    const restored = loadGridRuntimeFromSession(9)

    expect(restored.columnOrder).toEqual(['name', 'status'])
    expect(restored.columnWidths.name).toBe(200)
    expect(restored.sort).toBeNull()
    expect(restored.groupBy).toBeNull()
    expect(restored.filters).toEqual({})
  })
})
