import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import type { Column, SubTableBinding, SubTableFieldProps } from '../subTableFieldTypes'
import {
  isLinkFormBoundToHostGrid,
  mergeSelfBoundLinkFormIntoParentRow,
} from '../subTableLinkFormRowMatch'
import { useSubTableLinkFormOpen } from '../useSubTableLinkFormOpen'

const ATM_TX_FIELDS = [
  { key: 'transaction_id', type: 'input', label: 'Transaction' },
  { key: 'assignee_id', type: 'input', label: 'Assignee' },
]

function atmTxBinding(bindingId: number, extra: Partial<SubTableBinding> = {}): SubTableBinding {
  return {
    bindingId,
    tableId: 50,
    tableName: 'ATM Transaction',
    bindingType: 'SUB_TABLE',
    bindingMode: 'EDITABLE',
    tableType: 'TABLE',
    tableDescription: '',
    columns: [],
    data: [],
    formFields: ATM_TX_FIELDS,
    ...extra,
  }
}

describe('isLinkFormBoundToHostGrid', () => {
  const col = {
    props: { boundSubTableBindingId: 1127, boundSubTableName: 'ATM Transaction' },
  }

  it('treats boundSubTableBindingId equal to the grid binding as self-bound', () => {
    expect(isLinkFormBoundToHostGrid(col, { bindingId: 1127, title: 'ATM Transaction', tableId: 50 })).toBe(true)
  })

  it('treats a sibling MI binding of the same table as self-bound', () => {
    expect(
      isLinkFormBoundToHostGrid(
        col,
        { bindingId: 1135, title: 'ATM Transaction', tableId: 50 },
        atmTxBinding(1127),
      ),
    ).toBe(true)
  })

  it('does not treat a nested child table (Correspondence) as self-bound', () => {
    const childCol = {
      props: { boundSubTableBindingId: 1141, boundSubTableName: 'ATM Correspondence' },
    }
    const childBinding = {
      bindingId: 1141,
      tableId: 51,
      tableName: 'ATM Correspondence',
    }
    expect(
      isLinkFormBoundToHostGrid(
        childCol,
        { bindingId: 1127, title: 'ATM Transaction', tableId: 50 },
        childBinding,
      ),
    ).toBe(false)
  })
})

describe('mergeSelfBoundLinkFormIntoParentRow', () => {
  it('writes modal fields onto the grid row and keeps nested children', () => {
    const parent = {
      transaction_id: 'ATM-DC-PW-TRANS-000008',
      assignee_id: 'old',
      __subTables__: { 1141: [{ correspondence_id: 'Corr-000008' }] },
    }
    const form = { transaction_id: 'ATM-DC-PW-TRANS-000008', assignee_id: 'liam' }
    const merged = mergeSelfBoundLinkFormIntoParentRow(parent, form)
    expect(merged.assignee_id).toBe('liam')
    expect(merged.__subTables__[1141][0].correspondence_id).toBe('Corr-000008')
  })
})

function stubDialog(binding: SubTableBinding | undefined) {
  return {
    activeLinkColumn: ref<Column | null>(null),
    activeLinkRowIndex: ref<number | null>(null),
    linkedSubTableRows: ref<any[]>([]),
    linkedFormData: ref<Record<string, any>>({}),
    linkFormDialogSnapshot: ref(null),
    linkFormDialogVisible: ref(false),
    resolveLinkBindingForColumn: () => binding,
    resolveLinkedFallbackRows: () => [] as any[],
    buildLinkFormPeerMap: () => new Map<number, number | null>(),
    collectLinkFormRowsFromProcessVariables: () => [] as any[],
    buildLinkedFormData: (b?: SubTableBinding) => {
      const raw =
        b?.data?.[0] && typeof b.data[0] === 'object' ? (b.data[0] as Record<string, any>) : {}
      if (!b?.formFields?.length) return { ...raw }
      const next: Record<string, any> = {}
      for (const field of b.formFields) next[field.key] = raw[field.key]
      if (raw.__subTables__) next.__subTables__ = raw.__subTables__
      return next
    },
  }
}

function stubScope() {
  const passthrough = (_row: unknown, rows: any[]) => rows
  return {
    filterLinkedChildRowsByParentAssignee: passthrough,
    pickBestLinkedChildRowsForParentRow: passthrough,
    filterLinkedChildRowsForParentRow: passthrough,
    filterRowsByMiLinkFormParent: passthrough,
    preferLinkedChildRowMatchingParent: passthrough,
    strictChildRowsForParentByFk: () => null,
    promoteBestRowForLinkFormModal: (rows: any[]) => ({ rows }),
    miLinkFormChildRowMatchesParent: () => false,
    backfillMiLinkFormModalFieldsFromParent: () => undefined,
  }
}

describe('handleLinkFormClick self-bound ATM Details', () => {
  it('fills Details from the clicked ATM Transaction row when nested 1127 is missing (MI isolate)', () => {
    const binding = atmTxBinding(1127)
    const dialog = stubDialog(binding)
    const props = {
      title: 'ATM Transaction',
      bindingId: 1135,
      tableId: 50,
      columns: [],
      suppressLinkFormInitialData: true,
      editable: true,
      linkedSubTableBindings: [binding],
    } as SubTableFieldProps
    const { handleLinkFormClick } = useSubTableLinkFormOpen(
      props,
      dialog as unknown as ReturnType<typeof import('../useSubTableLinkFormDialog').useSubTableLinkFormDialog>,
      stubScope() as unknown as ReturnType<typeof import('../useSubTableLinkFormScope').useSubTableLinkFormScope>,
    )
    const col: Column = {
      field: 'linkForm:-1127',
      label: 'Link Form',
      type: 'linkForm',
      props: { boundSubTableBindingId: 1127, boundSubTableName: 'ATM Transaction', linkText: 'Details' },
    }
    const row = {
      transaction_id: 'ATM-DC-PW-TRANS-000008',
      assignee_id: '9b9e94f5-7e69-4ed2-af2e-573d17a09943',
      __subTables__: {
        1141: [{ correspondence_id: 'Corr-000008' }],
        1128: [],
      },
    }
    handleLinkFormClick(col, row, 0)
    expect(dialog.linkFormDialogVisible.value).toBe(true)
    expect(dialog.linkedFormData.value.transaction_id).toBe('ATM-DC-PW-TRANS-000008')
    expect(dialog.linkedFormData.value.assignee_id).toBe('9b9e94f5-7e69-4ed2-af2e-573d17a09943')
    expect(dialog.linkedSubTableRows.value[0].transaction_id).toBe('ATM-DC-PW-TRANS-000008')
  })
})
