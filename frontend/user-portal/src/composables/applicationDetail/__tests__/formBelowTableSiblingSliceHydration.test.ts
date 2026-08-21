import { describe, expect, it } from 'vitest'
import { getSavedSubTableRowsFromVariables } from '../subTableRowHelpers'
import { getSavedSubTableRows } from '../../tasks/shared'

/**
 * Form-below-table Save writes Y under the current binding id / table-name keys while older
 * node slices keep N. My Request and To Do must reopen as Y (same row_id, no id/id_idw).
 */
describe('form-below-table sibling slice hydration', () => {
  const stale = {
    row_id: 'TRANS-FBT-1',
    merchant_name: 'FBT',
    merchant_credit: 'N',
    temporary_refund: 'N',
  }
  const saved = {
    row_id: 'TRANS-FBT-1',
    merchant_name: 'FBT',
    merchant_credit: 'Y',
    temporary_refund: 'N',
  }
  const subTables = {
    '50522': [stale],
    '50527': [saved],
    '50533': [stale],
    'ATM Transaction': [saved],
  }

  it('My Request merges binding-id N with table-name Y by row_id', () => {
    const rows = getSavedSubTableRowsFromVariables(
      subTables,
      { bindingId: 50522, tableName: 'ATM_Transaction', tableDisplayName: 'ATM Transaction' },
      ['id_idw'],
    )
    expect(rows).toHaveLength(1)
    expect(rows?.[0].merchant_credit).toBe('Y')
  })

  it('My Request hydrates leftover binding 50533 N with table-name Y', () => {
    const rows = getSavedSubTableRowsFromVariables(
      subTables,
      { bindingId: 50533, tableName: 'ATM_Transaction', tableDisplayName: 'ATM Transaction' },
      ['id_idw'],
    )
    expect(rows).toHaveLength(1)
    expect(rows?.[0].merchant_credit).toBe('Y')
  })

  it('To Do hydrates binding-id N with sibling Y when name fallback is forbidden (shared display name)', () => {
    const rows = getSavedSubTableRows(
      subTables,
      {
        bindingId: 50533,
        tableName: 'ATM Transaction',
        tableId: 50327,
        primaryKeyFields: ['id_idw'],
        columns: [{ field: 'merchant_name' }, { field: 'card_number' }],
      },
      true,
      new Map([
        [50533, 50327],
        [50527, 50327],
      ]),
    )
    expect(rows).toHaveLength(1)
    expect(rows?.[0].merchant_credit).toBe('Y')
  })

  it('To Do hydrates binding-id N with table-name Y even when the list is not an MI dashboard', () => {
    const rows = getSavedSubTableRows(
      subTables,
      {
        bindingId: 50533,
        tableName: 'ATM Transaction',
        tableId: 50327,
        primaryKeyFields: ['id_idw'],
        columns: [{ field: 'merchant_name' }, { field: 'card_number' }],
      },
      false,
    )
    expect(rows).toHaveLength(1)
    expect(rows?.[0].merchant_credit).toBe('Y')
  })

  it('To Do MI dashboard merge of stale binding-id N with sibling Y keeps Y', () => {
    const rows = getSavedSubTableRows(
      subTables,
      {
        bindingId: 50522,
        tableName: 'ATM Transaction',
        tableId: 50327,
        primaryKeyFields: ['id_idw'],
        columns: [{ field: 'assignee' }, { field: 'merchant_name' }],
      },
      false,
      new Map([
        [50522, 50327],
        [50527, 50327],
      ]),
    )
    expect(rows).toHaveLength(1)
    expect(rows?.[0].merchant_credit).toBe('Y')
  })

  it('To Do current binding Y is not overwritten by leftover sibling N', () => {
    const rows = getSavedSubTableRows(
      subTables,
      {
        bindingId: 50527,
        tableName: 'ATM Transaction',
        tableId: 50327,
        primaryKeyFields: ['id_idw'],
        columns: [{ field: 'merchant_name' }, { field: 'card_number' }],
      },
      false,
      new Map([
        [50522, 50327],
        [50527, 50327],
        [50533, 50327],
      ]),
    )
    expect(rows).toHaveLength(1)
    expect(rows?.[0].merchant_credit).toBe('Y')
  })
})
