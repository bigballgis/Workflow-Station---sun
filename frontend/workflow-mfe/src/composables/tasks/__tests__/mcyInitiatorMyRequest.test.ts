import { describe, expect, it } from 'vitest'
import {
  enrichChildBindingRowsFromParentsNestedSubTables,
  flattenNestedSubTableRowsIntoPayload,
  hydrateChildSubTablesFromParentsNestedRows,
  hydrateBindingsRowsFromVariablesBySharedRelationTableId,
  mergeAllSubTableSlicesFromVariables,
  mergeSubTableRowsByRowId,
  subTableVariablesIncludeMiRows,
  dropSubsumedSubTableRows,
} from '../shared'

function getSavedSubTableRowsFromVariables(
  savedSubTables: Record<string, any>,
  rawBinding: { bindingId: number; tableName?: string; tableDisplayName?: string },
): any[] | undefined {
  const keys = [rawBinding.bindingId, String(rawBinding.bindingId), rawBinding.tableName, rawBinding.tableDisplayName]
  for (const key of keys) {
    if (key == null || key === '') continue
    const v = savedSubTables[key as string]
    if (Array.isArray(v) && v.length > 0) return [...v]
  }
  return undefined
}
import {
  filterBindingsToMiParticipantRow,
  resolveMiSubProcessScopeFromBpmn,
  resolveViewerParticipantRowIdFromCollectionBinding,
} from '../miSubProcessScope'

/** Live snapshot from process fcef7f80-59b6-11f1-9eab-2abd28d84436 (2026-05-27). */
const SAVED_SUB_TABLES = {
  '271': [],
  '273': [{ file: '/api/v1/upload/files/test.pdf' }],
  '281': [
    { row_id: '4555', assignee_id: { id: 'user-dev' }, task_status: 'IN_PROGRESS' },
    { row_id: '777', assignee_id: { id: 'user-dev' }, task_status: 'IN_PROGRESS' },
  ],
  '285': [
    {
      row_id: '777',
      assignee_id: { id: 'user-dev' },
      task_status: 'IN_PROGRESS',
      __subTables__: {
        '287': [{ file: '/api/v1/upload/files/test.pdf' }],
      },
    },
  ],
  '287': [{ file: '/api/v1/upload/files/test.pdf' }],
} as Record<string, unknown>

const RT_MAP = new Map<number, number | null>([
  [281, 112],
  [285, 112],
  [273, 114],
  [287, 114],
])

const MCY_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:flowable="http://flowable.org/bpmn"
  xmlns:custom="http://workflow.platform/schema/custom">
  <bpmn:process id="Process_MCY">
    <bpmn:subProcess id="Activity_0r2315n">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="miTaskStatusField" value="sub_task_status" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:multiInstanceLoopCharacteristics
        flowable:collection="multiInstance_HMDC_Transaction_collection"
        flowable:elementVariable="currentItem" />
      <bpmn:userTask id="Activity_1c23xsu" name="Transaction Investigation">
        <bpmn:extensionElements>
          <custom:properties>
            <custom:property name="subTableName" value="HMDC_Transaction" />
            <custom:property name="assigneeField" value="assignee_id" />
          </custom:properties>
        </bpmn:extensionElements>
      </bpmn:userTask>
    </bpmn:subProcess>
  </bpmn:process>
</bpmn:definitions>`

function buildForm165Bindings() {
  const bindings = [
    {
      bindingId: 285,
      tableId: 112,
      tableName: 'HMDC Transaction',
      physicalTableName: 'HMDC_Transaction',
      foreignKeyField: 'row_id',
      columns: [{ field: 'row_id' }, { field: 'card_number' }],
      primaryKeyFields: ['row_id'],
      data: [] as any[],
    },
    {
      bindingId: 287,
      tableId: 114,
      tableName: 'HMDC Attachment',
      physicalTableName: 'HMDC_Attachment',
      foreignKeyField: 'row_id',
      columns: [{ field: 'file' }],
      primaryKeyFields: ['row_id'],
      data: [] as any[],
    },
  ]
  const saved = structuredClone(SAVED_SUB_TABLES)
  flattenNestedSubTableRowsIntoPayload(saved)
  for (const b of bindings) {
    const rows = getSavedSubTableRowsFromVariables(saved, {
      bindingId: b.bindingId,
      tableName: b.physicalTableName,
      tableDisplayName: b.tableName,
    })
    if (rows) b.data = rows
  }
  hydrateChildSubTablesFromParentsNestedRows(bindings, saved, RT_MAP)
  hydrateBindingsRowsFromVariablesBySharedRelationTableId(bindings, saved, RT_MAP)
  enrichChildBindingRowsFromParentsNestedSubTables(bindings)
  return bindings
}

describe('MCY initiator My Request — Transaction Investigation form', () => {
  it('hydrates binding 285 and 287 from live __subTables__ snapshot', () => {
    const bindings = buildForm165Bindings()
    expect(bindings.find(b => b.bindingId === 285)!.data.length).toBeGreaterThan(0)
    expect(bindings.find(b => b.bindingId === 287)!.data.length).toBeGreaterThan(0)
  })

  it('MI participant filter (assignee viewer) keeps one transaction when same user owns both rows', () => {
    const bindings = buildForm165Bindings()
    // Simulate union merge from binding 281 (assignment step) into 285
    bindings.find(b => b.bindingId === 285)!.data = [
      ...(SAVED_SUB_TABLES['281'] as any[]),
    ]
    const scope = resolveMiSubProcessScopeFromBpmn(MCY_BPMN, {
      userTaskName: 'Transaction Investigation',
    })!
    const collection = bindings.find(b => b.bindingId === 285)!
    const participant = resolveViewerParticipantRowIdFromCollectionBinding(scope, collection, 'user-dev')
    expect(participant).toBe('4555')
    filterBindingsToMiParticipantRow(bindings, scope, participant!)
    expect(bindings.find(b => b.bindingId === 285)!.data).toHaveLength(1)
    expect((bindings.find(b => b.bindingId === 285)!.data[0] as any).row_id).toBe('4555')
  })

  it('attachment binding 287 retains file rows after MI participant filter', () => {
    const bindings = buildForm165Bindings()
    const scope = resolveMiSubProcessScopeFromBpmn(MCY_BPMN, {
      userTaskName: 'Transaction Investigation',
    })!
    const collection = bindings.find(b => b.bindingId === 285)!
    const participant = resolveViewerParticipantRowIdFromCollectionBinding(scope, collection, 'user-dev')!
    filterBindingsToMiParticipantRow(bindings, scope, participant)
    const att = bindings.find(b => b.bindingId === 287)!
    expect(att.data.length).toBeGreaterThan(0)
    expect((att.data[0] as any).file).toContain('test.pdf')
  })

  it('file-only attachment must not absorb global MI transaction slices (2 empty file rows)', () => {
    const bindings = buildForm165Bindings()
    bindings.find(b => b.bindingId === 285)!.data = [...(SAVED_SUB_TABLES['281'] as any[])]
    const saved = structuredClone(SAVED_SUB_TABLES)
    flattenNestedSubTableRowsIntoPayload(saved)
    const att = bindings.find(b => b.bindingId === 287)!
    const pk = att.primaryKeyFields ?? null
    const bindingSaved = getSavedSubTableRowsFromVariables(saved, {
      bindingId: 287,
      tableName: 'HMDC_Attachment',
      tableDisplayName: 'HMDC Attachment',
    })
    // Wrong path (pre-fix): merge all __subTables__ slices — leaks 281 transaction rows into attachment.
    const useAllSlices = subTableVariablesIncludeMiRows(saved)
    const allSlicesMerged = useAllSlices ? mergeAllSubTableSlicesFromVariables(saved, undefined) : []
    const wrongMerge = dropSubsumedSubTableRows(
      mergeSubTableRowsByRowId(att.data, mergeSubTableRowsByRowId(allSlicesMerged, bindingSaved ?? [], pk), pk),
    )
    expect(wrongMerge.length).toBeGreaterThan(1)
    expect(wrongMerge.some((r: any) => r?.row_id && !r?.file)).toBe(true)

    // Correct path: own binding slice only.
    att.data = dropSubsumedSubTableRows(
      mergeSubTableRowsByRowId(Array.isArray(att.data) ? att.data : [], bindingSaved ?? [], pk),
    )
    expect(att.data).toHaveLength(1)
    expect((att.data[0] as any).file).toContain('test.pdf')
  })
})
