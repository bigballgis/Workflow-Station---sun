import { describe, expect, it } from 'vitest'
import {
  bindingMatchesMiSubTableName,
  filterBindingsToMiParticipantRow,
  extractMiParticipantRowIdFromCurrentItem,
  normalizeMiParticipantRowId,
  resolveMiSubProcessScopeFromBpmn,
  resolveViewerParticipantRowIdFromCollectionBinding,
  rowMatchesSubTablePrimaryKey,
} from '../miSubProcessScope'

const MCY_MI_BPMN_SNIPPET = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:flowable="http://flowable.org/bpmn"
  xmlns:custom="http://workflow.platform/schema/custom"
  id="Definitions_1">
  <bpmn:process id="Process_MCY" isExecutable="true">
    <bpmn:subProcess id="Activity_0r2315n" name="Multi-Instance: Transaction">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="miTaskStatusField" value="sub_task_status" />
          <custom:property name="miTaskCurrentNodeField" value="sub_task_current_node" />
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
            <custom:property name="rowIdVariable" value="currentItem.rowId" />
          </custom:properties>
        </bpmn:extensionElements>
      </bpmn:userTask>
    </bpmn:subProcess>
  </bpmn:process>
</bpmn:definitions>`

describe('miSubProcessScope', () => {
  it('resolveMiSubProcessScopeFromBpmn reads Process Design subTableName and MI columns', () => {
    const scope = resolveMiSubProcessScopeFromBpmn(MCY_MI_BPMN_SNIPPET, {
      userTaskName: 'Transaction Investigation',
    })
    expect(scope).not.toBeNull()
    expect(scope!.subTableName).toBe('HMDC_Transaction')
    expect(scope!.assigneeField).toBe('assignee_id')
    expect(scope!.miTaskStatusField).toBe('sub_task_status')
    expect(scope!.rowIdVariable).toBe('currentItem.rowId')
  })

  it('rowMatchesSubTablePrimaryKey uses designer PK fields only', () => {
    expect(rowMatchesSubTablePrimaryKey({ row_id: 232424 }, 232424, ['row_id'])).toBe(true)
    expect(rowMatchesSubTablePrimaryKey({ row_id: 57666 }, 232424, ['row_id'])).toBe(false)
    expect(rowMatchesSubTablePrimaryKey({ id_idw: 88 }, 88, ['id_idw'])).toBe(true)
    expect(rowMatchesSubTablePrimaryKey({ row_id: 232424 }, 232424, [])).toBe(false)
    expect(rowMatchesSubTablePrimaryKey({ row_id: 232424 }, 232424, undefined)).toBe(false)
  })

  it('rowMatchesSubTablePrimaryKey supports string and UUID participant ids', () => {
    expect(
      rowMatchesSubTablePrimaryKey(
        { txn_uid: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890' },
        'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
        ['txn_uid'],
      ),
    ).toBe(true)
    expect(
      rowMatchesSubTablePrimaryKey(
        { txn_uid: 'other-uuid' },
        'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
        ['txn_uid'],
      ),
    ).toBe(false)
  })

  it('normalizeMiParticipantRowId preserves string UUIDs', () => {
    expect(normalizeMiParticipantRowId('uuid-abc')).toBe('uuid-abc')
    expect(normalizeMiParticipantRowId(42)).toBe(42)
    expect(normalizeMiParticipantRowId('')).toBeNull()
  })

  it('bindingMatchesMiSubTableName matches physical and display labels', () => {
    expect(
      bindingMatchesMiSubTableName(
        { designerTableName: 'HMDC_Transaction', tableName: 'HMDC Transaction' },
        'HMDC_Transaction',
      ),
    ).toBe(true)
  })

  it('filterBindingsToMiParticipantRow scopes child rows when PK supplied but collection binding is absent', () => {
    const scope = resolveMiSubProcessScopeFromBpmn(MCY_MI_BPMN_SNIPPET, {
      userTaskName: 'Transaction Investigation',
    })!
    const bindings = [
      {
        bindingId: 63,
        designerTableName: 'People',
        tableName: 'People',
        foreignKeyField: 'sub_task_id',
        primaryKeyFields: ['id'],
        data: [
          { id: 'a', sub_task_id: 'Test-001', age: 5 },
          { id: 'b', sub_task_id: 'Test-002', age: 6 },
        ],
      },
    ]
    filterBindingsToMiParticipantRow(bindings, scope, 'Test-001', {
      participantPrimaryKeyFields: ['row_id'],
    })
    expect(bindings[0].data).toHaveLength(1)
    expect((bindings[0].data![0] as { sub_task_id: string }).sub_task_id).toBe('Test-001')
  })

  it('filterBindingsToMiParticipantRow scopes collection table by designer PK', () => {
    const scope = resolveMiSubProcessScopeFromBpmn(MCY_MI_BPMN_SNIPPET, {
      userTaskName: 'Transaction Investigation',
    })!
    const bindings = [
      {
        bindingId: 285,
        designerTableName: 'HMDC_Transaction',
        tableName: 'HMDC Transaction',
        primaryKeyFields: ['row_id'],
        data: [{ row_id: 232424, assignee_id: 'u1' }, { row_id: 57666, assignee_id: 'u2' }],
      },
    ]
    filterBindingsToMiParticipantRow(bindings, scope, 232424)
    expect(bindings[0].data).toHaveLength(1)
    expect((bindings[0].data![0] as { row_id: number }).row_id).toBe(232424)
  })

  it('resolveViewerParticipantRowIdFromCollectionBinding uses BPMN assigneeField + PK', () => {
    const scope = resolveMiSubProcessScopeFromBpmn(MCY_MI_BPMN_SNIPPET, {
      userTaskName: 'Transaction Investigation',
    })!
    const binding = {
      designerTableName: 'HMDC_Transaction',
      tableName: 'HMDC Transaction',
      primaryKeyFields: ['row_id'],
      data: [{ row_id: 232424, assignee_id: 'u1' }, { row_id: 57666, assignee_id: 'u2' }],
    }
    expect(resolveViewerParticipantRowIdFromCollectionBinding(scope, binding, 'u2')).toBe(57666)
  })

  it('extractMiParticipantRowIdFromCurrentItem reads composite PK from rowKey', () => {
    const id = extractMiParticipantRowIdFromCurrentItem(
      {
        rowKey: { tenant_id: 'T1', line_no: 3 },
        assignee_id: 'u1',
      },
      ['tenant_id', 'line_no'],
    )
    expect(id).toBe('T1|3')
  })

  it('extractMiParticipantRowIdFromCurrentItem single PK prefers rowKey then rowId', () => {
    expect(
      extractMiParticipantRowIdFromCurrentItem(
        { rowId: 99, rowKey: { row_id: 232424 }, assignee_id: 'u1' },
        ['row_id'],
      ),
    ).toBe(232424)
    expect(
      extractMiParticipantRowIdFromCurrentItem({ rowId: 57666, assignee_id: 'u1' }, ['row_id']),
    ).toBe(57666)
  })

  it('rowMatchesSubTablePrimaryKey matches composite id and nested rowKey', () => {
    expect(
      rowMatchesSubTablePrimaryKey(
        { rowKey: { tenant_id: 'T1', line_no: 3 }, name: 'x' },
        'T1|3',
        ['tenant_id', 'line_no'],
      ),
    ).toBe(true)
    expect(
      rowMatchesSubTablePrimaryKey(
        { tenant_id: 'T1', line_no: 9 },
        'T1|3',
        ['tenant_id', 'line_no'],
      ),
    ).toBe(false)
  })

  it('filterBindingsToMiParticipantRow scopes composite PK collection rows', () => {
    const scope = resolveMiSubProcessScopeFromBpmn(MCY_MI_BPMN_SNIPPET, {
      userTaskName: 'Transaction Investigation',
    })!
    const bindings = [
      {
        bindingId: 1,
        designerTableName: 'HMDC_Transaction',
        tableName: 'HMDC Transaction',
        primaryKeyFields: ['tenant_id', 'line_no'],
        data: [
          { tenant_id: 'T1', line_no: 1, assignee_id: 'u1' },
          { tenant_id: 'T1', line_no: 2, assignee_id: 'u2' },
        ],
      },
    ]
    filterBindingsToMiParticipantRow(bindings, scope, 'T1|2')
    expect(bindings[0].data).toHaveLength(1)
    expect((bindings[0].data![0] as { line_no: number }).line_no).toBe(2)
  })
})
