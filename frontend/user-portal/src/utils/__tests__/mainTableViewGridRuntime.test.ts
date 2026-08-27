import { describe, expect, it } from 'vitest'
import {
  clampColumnWidth,
  columnWidth,
  createDefaultGridRuntime,
  loadGridRuntimeFromSession,
  moveColumn,
  saveGridRuntimeToSession,
  setColumnWidth,
  toListColumnMeta,
} from '../mainTableViewGridRuntime'

describe('mainTableViewGridRuntime', () => {
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

  it('uses the designer columnWidth before measuring the header label', () => {
    const state = createDefaultGridRuntime()
    expect(
      columnWidth(
        {
          fieldName: 'status',
          displayLabel: 'A',
          columnWidth: 220,
          kind: 'TEXT',
          filterable: true,
          sortable: true,
          operators: ['eq'],
        },
        state,
      ),
    ).toBe(220)
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
    state.filters.status = { operator: 'eq', value: 'Open' }

    saveGridRuntimeToSession(9, state)
    const restored = loadGridRuntimeFromSession(9)

    expect(restored.columnOrder).toEqual(['name', 'status'])
    expect(restored.columnWidths.name).toBe(200)
    expect(restored.sort).toBeNull()
    expect(restored.filters).toEqual({})
  })
})
