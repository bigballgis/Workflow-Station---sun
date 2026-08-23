import { describe, it, expect } from 'vitest'
import { isReactive, reactive, toRaw } from 'vue'
import { finalizeSharedProcessSubTableBindingRows } from '../sharedProcessSubTableFilters'
import { stripNestedSubTablesFromRows } from '../subTableCore'
import { hydrateChildSubTablesFromParentsNestedRows, pullNestedRowsForBindingFromParentRows } from '../subTableNestedRows'

/**
 * Sub-table-in-sub-table (3 levels) integrity, reproduced from dev FU 50011
 * "Nested Sub-Table Demo": nst_order (50112) → nst_shipment (50113) → nst_package (50114).
 *
 * Observed end-to-end before the fix: the start form persisted the package rows correctly
 * (both under `shipmentRow.__subTables__["50114"]` and in the flat `__subTables__["50114"]`
 * slice), but on the NEXT task the shipment row's Edit dialog showed an empty Package grid and
 * one Save rewrote the process variables as:
 *   50113: [ {shipment_name:"S-9", carrier:"FedEx"},            ← __subTables__ gone
 *            {package_label:"P-9", weight_kg:"7", id_idw:new} ] ← package row as a phantom shipment
 * Both halves are covered here.
 */

const SHIP_BINDING = { bindingId: 50113, tableName: 'nst_shipment', tableId: 50080 }
const PKG_BINDING = { bindingId: 50114, tableName: 'nst_package', tableId: 50081 }

function packageRow() {
  return { package_label: 'P-9', weight_kg: '7', attachment_file: '' }
}

function shipmentRow() {
  return {
    id_idw: 'ship-1',
    shipment_name: 'S-9',
    carrier: 'FedEx',
    __subTables__: {
      '50114': [packageRow()],
      // designer alias key written alongside the numeric one by the persist path
      Package: [packageRow()],
    },
  }
}

describe('3-level nesting: the parent row keeps its grandchild rows', () => {
  it('finalize keeps a non-empty __subTables__ on the parent row', () => {
    const out = finalizeSharedProcessSubTableBindingRows([shipmentRow()], {
      columns: [{ field: 'shipment_name' }, { field: 'carrier' }],
      tableName: 'nst_shipment',
      tableId: 50080,
    })
    expect(out).toHaveLength(1)
    const nested = (out[0] as Record<string, any>).__subTables__
    expect(nested?.['50114']).toHaveLength(1)
    expect(nested['50114'][0].package_label).toBe('P-9')
  })

  it('finalize still drops MI meta and an empty nested map', () => {
    const out = finalizeSharedProcessSubTableBindingRows(
      [{ shipment_name: 'S-9', rowKey: { id: 'ship-1' }, __subTables__: {} }],
      { columns: [{ field: 'shipment_name' }], tableName: 'nst_shipment', tableId: 50080 },
    )
    expect(out[0]).toEqual({ shipment_name: 'S-9' })
  })

  it('the pre-mount detach keeps the nested rows but out of the reactive graph', () => {
    const rows = [shipmentRow()]
    stripNestedSubTablesFromRows(rows)
    const nested = (rows[0] as Record<string, any>).__subTables__
    expect(nested['50114'][0].package_label).toBe('P-9')
    // markRaw: Vue never deep-walks the nested rows (the reason the payload used to be deleted).
    const reactiveRow = reactive(rows[0] as Record<string, any>)
    expect(isReactive(reactiveRow.__subTables__)).toBe(false)
    expect(toRaw(reactiveRow.__subTables__)).toBe(nested)
  })

  it('the pre-mount detach still removes an empty nested map', () => {
    const rows = [{ shipment_name: 'S-9', __subTables__: { '50114': [] } }]
    stripNestedSubTablesFromRows(rows)
    expect(rows[0]).toEqual({ shipment_name: 'S-9' })
  })

  it('the dialog reader still resolves the grandchild rows off the finalized parent row', () => {
    const [row] = finalizeSharedProcessSubTableBindingRows([shipmentRow()], {
      columns: [{ field: 'shipment_name' }, { field: 'carrier' }],
      tableName: 'nst_shipment',
      tableId: 50080,
    })
    const rows = pullNestedRowsForBindingFromParentRows({ ...PKG_BINDING }, [row])
    expect(rows.map(r => r.package_label)).toEqual(['P-9'])
  })
})

describe('3-level nesting: grandchild rows never become parent rows', () => {
  const savedSubTables = () => ({
    '50113': [shipmentRow()],
    Shipment: [shipmentRow()],
    '50114': [{ id_idw: 'pkg-1', ...packageRow() }],
  })

  function bindings() {
    return [
      { ...SHIP_BINDING, data: [shipmentRow()], primaryKeyFields: ['id_idw'] },
      { ...PKG_BINDING, data: [{ id_idw: 'pkg-1', ...packageRow() }], primaryKeyFields: ['id_idw'] },
    ]
  }

  it('hydration leaves the middle binding with only its own rows', () => {
    const bs = bindings()
    hydrateChildSubTablesFromParentsNestedRows(bs as any, savedSubTables() as any)
    const ship = bs.find(b => b.bindingId === 50113)!
    expect(ship.data).toHaveLength(1)
    expect((ship.data[0] as any).shipment_name).toBe('S-9')
    expect(ship.data.some((r: any) => r.package_label != null)).toBe(false)
  })

  it('hydration still fills the innermost binding when its flat slice is missing', () => {
    const bs = [
      { ...SHIP_BINDING, data: [shipmentRow()], primaryKeyFields: ['id_idw'] },
      { ...PKG_BINDING, data: [] as any[], primaryKeyFields: ['id_idw'] },
    ]
    hydrateChildSubTablesFromParentsNestedRows(bs as any, { '50113': [shipmentRow()] } as any)
    const pkg = bs.find(b => b.bindingId === 50114)!
    expect(pkg.data.map((r: any) => r.package_label)).toEqual(['P-9'])
  })

  it('a nested slice keyed by a stale binding id of the SAME table is still adopted', () => {
    // Legacy payload: the nested map still uses a previous node's binding id for nst_package.
    const stale = {
      id_idw: 'ship-1',
      shipment_name: 'S-9',
      __subTables__: { '99114': [packageRow()] },
    }
    const bs = [
      { ...SHIP_BINDING, data: [stale], primaryKeyFields: ['id_idw'] },
      { ...PKG_BINDING, data: [] as any[], primaryKeyFields: ['id_idw'] },
    ]
    const map = new Map<number, number | null>([[99114, 50081]])
    hydrateChildSubTablesFromParentsNestedRows(bs as any, null, map)
    expect((bs[1]!.data as any[]).map(r => r.package_label)).toEqual(['P-9'])
  })

  /**
   * Regression: a nested slice whose key resolves to an unknown/null tableId (e.g. a RELATED lookup
   * binding, or any binding this map has no entry for) must never be guessed as belonging to an
   * unrelated sibling binding just because it's the only other numeric-keyed slice on the row. This
   * previously fabricated a phantom row (no primary key, only whatever fields the stale slice held)
   * in My Request's Participants grid, sourced from a Meeting Remark row nested under an unrelated
   * RELATED (lookup) binding id.
   */
  it('a nested slice with unknown/null tableId is never guessed as belonging to an unrelated sibling', () => {
    const rowWithUnattributableSlice = {
      id_idw: 'ship-1',
      shipment_name: 'S-9',
      __subTables__: {
        // '77000' has no entry in bindingTableById at all (unknown), '77001' has an entry but tableId=null
        // (e.g. a RELATED/lookup binding) — neither should ever be adopted as nst_package's own rows.
        '77000': [{ unrelated_field: 'x' }],
        '77001': [{ another_unrelated_field: 'y' }],
      },
    }
    const bs = [
      { ...SHIP_BINDING, data: [rowWithUnattributableSlice], primaryKeyFields: ['id_idw'] },
      { ...PKG_BINDING, data: [] as any[], primaryKeyFields: ['id_idw'] },
    ]
    const map = new Map<number, number | null>([[77001, null]])
    hydrateChildSubTablesFromParentsNestedRows(bs as any, null, map)
    const pkg = bs.find(b => b.bindingId === 50114)!
    expect(pkg.data).toEqual([])
  })
})
