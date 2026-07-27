import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick, ref } from 'vue'

const fetchMock = vi.fn()
vi.mock('@/components/lookup/fetchLookupRowByPrimaryKey', () => ({
  fetchLookupRowByPrimaryKey: (...args: unknown[]) => fetchMock(...args),
}))

import { lookupSelectedRow, useSubTableLookupCells } from '../useSubTableLookupCells'
import type { Column, SubTableFieldProps } from '../subTableFieldTypes'

function lookupColumn(field: string, props: Record<string, unknown>): Column {
  return { field, label: field, type: 'lookup', props } as unknown as Column
}

function makeProps(columns: Column[], extra: Partial<SubTableFieldProps> = {}): SubTableFieldProps {
  return { title: 'sub', columns, ...extra } as SubTableFieldProps
}

/** watcher 走 nextTick + setTimeout(80) 兜底（jsdom 无 requestIdleCallback） */
async function flushHydration(): Promise<void> {
  await nextTick()
  await new Promise(resolve => setTimeout(resolve, 120))
  await nextTick()
  await new Promise(resolve => setTimeout(resolve, 0))
}

describe('useSubTableLookupCells', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    fetchMock.mockResolvedValue(null)
  })

  it('wraps a scalar under the relation table PK (searchFields[0]), not hardcoded id', () => {
    const col = lookupColumn('vendor', { tableId: 7, searchFields: ['vendor_code'] })
    expect(lookupSelectedRow(col, 'V-1')).toEqual({ vendor_code: 'V-1' })
  })

  it('honors an explicit designer primaryKeyField over searchFields[0]', () => {
    const col = lookupColumn('vendor', {
      tableId: 7,
      primaryKeyField: 'uuid',
      searchFields: ['vendor_code'],
    })
    expect(lookupSelectedRow(col, 'V-1')).toEqual({ uuid: 'V-1' })
  })

  it('hydrates with the relation table PK — never the host sub-table primaryKeyFields', async () => {
    const col = lookupColumn('vendor', { tableId: 7, searchFields: ['vendor_code'] })
    const rows = ref<Array<Record<string, unknown>>>([{ vendor: 'V-1' }])
    // 宿主子表自己的主键列：拿它去查关联表会让 eq 条件落到不存在的列上
    const props = makeProps([col], { primaryKeyFields: ['order_line_id'] })
    const { effectiveLookupRowForCell } = useSubTableLookupCells(props, rows)

    fetchMock.mockResolvedValue({ vendor_code: 'V-1', vendor_name: 'Acme' })
    await flushHydration()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe(7)
    expect(fetchMock.mock.calls[0][1]).toBe('V-1')
    expect(fetchMock.mock.calls[0][2]).toMatchObject({ primaryKeyField: 'vendor_code' })
    expect(effectiveLookupRowForCell(col, 'V-1')).toEqual({ vendor_code: 'V-1', vendor_name: 'Acme' })
  })

  it('does not reuse a hydrated row across columns of the same table with different PKs', async () => {
    const byCode = lookupColumn('vendor', { tableId: 7, searchFields: ['vendor_code'] })
    const byUuid = lookupColumn('vendorRef', { tableId: 7, primaryKeyField: 'uuid', searchFields: ['vendor_code'] })
    const rows = ref<Array<Record<string, unknown>>>([{ vendor: 'V-1', vendorRef: 'V-1' }])
    const { effectiveLookupRowForCell } = useSubTableLookupCells(makeProps([byCode, byUuid]), rows)

    fetchMock.mockImplementation((_tableId: number, _scalar: string, opts: { primaryKeyField: string }) =>
      Promise.resolve(opts.primaryKeyField === 'vendor_code' ? { vendor_code: 'V-1', vendor_name: 'Acme' } : null),
    )
    await flushHydration()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(effectiveLookupRowForCell(byCode, 'V-1')).toEqual({ vendor_code: 'V-1', vendor_name: 'Acme' })
    // uuid 列没解析出行 —— 不能借用 vendor_code 列的缓存
    expect(effectiveLookupRowForCell(byUuid, 'V-1')).toEqual({ uuid: 'V-1' })
  })

  it('fetches once per distinct scalar and skips blank cells', async () => {
    const col = lookupColumn('vendor', { tableId: 7, searchFields: ['vendor_code'] })
    const rows = ref<Array<Record<string, unknown>>>([{ vendor: 'V-1' }, { vendor: 'V-1' }, { vendor: '' }, { vendor: null }])
    useSubTableLookupCells(makeProps([col]), rows)

    await flushHydration()

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('throttles hydration to 12 concurrent requests', async () => {
    const col = lookupColumn('vendor', { tableId: 7, searchFields: ['vendor_code'] })
    const rows = ref<Array<Record<string, unknown>>>(Array.from({ length: 20 }, (_, i) => ({ vendor: `V-${i}` })))
    let inFlight = 0
    let peak = 0
    fetchMock.mockImplementation(() => {
      inFlight += 1
      peak = Math.max(peak, inFlight)
      return new Promise(resolve => setTimeout(() => { inFlight -= 1; resolve(null) }, 0))
    })
    useSubTableLookupCells(makeProps([col]), rows)

    await flushHydration()

    expect(fetchMock).toHaveBeenCalledTimes(20)
    expect(peak).toBeLessThanOrEqual(12)
  })
})
