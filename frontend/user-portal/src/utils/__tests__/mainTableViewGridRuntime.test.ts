import { describe, expect, it } from 'vitest'
import {
  clampColumnWidth,
  columnWidth,
  createDefaultGridRuntime,
  loadGridRuntimeFromSession,
  migrateMtvWidthsToListLayout,
  moveColumn,
  saveGridRuntimeToSession,
  setColumnWidth,
  toListColumnMeta,
} from '../mainTableViewGridRuntime'
import { KIND_CONTENT_FLOOR, headerFitColumnWidth } from '@platform-shared/list/columnWidthLayout'

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

  it('uses the kind content floor when the header is shorter than typical values', () => {
    const state = createDefaultGridRuntime()
    expect(
      columnWidth(
        {
          fieldName: 'request_id',
          displayLabel: 'ID',
          kind: 'TEXT',
          filterable: true,
          sortable: true,
          operators: ['eq'],
        },
        state,
      ),
    ).toBe(KIND_CONTENT_FLOOR.TEXT)
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

  it('does not let a remembered width crop a Views header', () => {
    const state = createDefaultGridRuntime()
    state.columnWidths.assignee = 60
    const col = {
      fieldName: 'assignee',
      displayLabel: 'Current Assignee',
      kind: 'USER' as const,
      filterable: true,
      sortable: true,
      operators: ['eq'],
    }
    expect(columnWidth(col, state)).toBe(headerFitColumnWidth('Current Assignee', 'USER'))
  })

  it('does not raise a designer columnWidth that is narrower than the header', () => {
    const state = createDefaultGridRuntime()
    expect(
      columnWidth(
        {
          fieldName: 'status',
          displayLabel: 'Current Assignee',
          columnWidth: 80,
          kind: 'USER',
          filterable: true,
          sortable: true,
          operators: ['eq'],
        },
        state,
      ),
    ).toBe(80)
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

  it('copies legacy mtv-layout widths into the shared list-layout session once', () => {
    sessionStorage.setItem(
      'portal-mtv-layout:11',
      JSON.stringify({ columnOrder: ['name'], columnWidths: { name: 240 } }),
    )
    migrateMtvWidthsToListLayout(11)
    expect(JSON.parse(sessionStorage.getItem('portal-list-layout:mtv:11') ?? '{}')).toEqual({
      v: 2,
      columnWidths: { name: 240 },
    })
    sessionStorage.setItem(
      'portal-list-layout:mtv:11',
      JSON.stringify({ v: 2, columnWidths: { name: 180 } }),
    )
    sessionStorage.setItem(
      'portal-mtv-layout:11',
      JSON.stringify({ columnWidths: { name: 999 } }),
    )
    migrateMtvWidthsToListLayout(11)
    expect(JSON.parse(sessionStorage.getItem('portal-list-layout:mtv:11') ?? '{}').columnWidths.name).toBe(180)
  })
})
