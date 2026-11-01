import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createApplicationDetailNodeFormMap } from '../useApplicationDetailNodeFormMap'
import { projectSavedRowsForBinding } from '@/composables/tasks/subTableFilterProjection'

function build(primaryKeyFields: string[] | undefined) {
  const rows = [{ key: 'a', dossier_ref: 'D-1', text: 'case note' },
    { key: 'b', item_ref: 'T-1', text: 'transaction note' }]
  const primary = { bindingType: 'PRIMARY', tableId: 11, primaryKeyFields,
    fieldDefinitions: [{ fieldName: 'dossier_key', isPrimaryKey: true }] }
  const child = { bindingId: 22, bindingType: 'SUB', tableId: 12,
    tableName: 'messages', primaryKeyFields: ['key'], bindingLinkMode: 'structuralFk',
    filterFkFieldName: 'dossier_ref', filterFkRefTableId: 11 }
  const ctx = {
    snapshotTaskName: '', snapshotTaskDefinitionKey: '', snapshotActivityId: ref(''),
    processInfo: ref({ status: 'COMPLETED' }), formData: ref({ dossier_key: 'D-1',
      __subTables__: { 'dw:messages': rows } }), selectedNodeId: ref(null),
    nodeFormMap: ref(new Map()), lastBindingRelationTableMap: ref(new Map()),
    extractFieldsRecursive: () => [], resolveSubTableBindingColumnsForPortal: () => [{ field: 'text' }],
    resolveSubFormDesign: () => ({}), mergeLinkFormTargetBindingsInto: vi.fn(),
    stripLinkOnlySubTableFieldsFromBindings: vi.fn(),
  }
  createApplicationDetailNodeFormMap(ctx as never).buildApplicationNodeFormMap({
    processes: [{ data: `<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
      <process id="p"><userTask id="entry" name="Submit"><extensionElements>
      <property name="formId" value="31" /></extensionElements></userTask></process></definitions>` }],
    forms: [{ sourceId: 31, name: 'Entry', data: { rule: [] }, tableBindings: [primary, child] }],
  })
  const node = ctx.nodeFormMap.value.get('entry')
  expect(node).toBeDefined()
  const binding = node.subTableBindings[0]
  return { primary, node, visible: projectSavedRowsForBinding(binding.data, binding,
    node.subTableBindings, { formData: node.values, primaryTableId: node.primaryTableBinding.tableId,
      primaryPkFields: node.primaryTableBinding.primaryKeyFields,
      primaryFieldDefinitions: node.primaryTableBinding.fieldDefinitions }) }
}

describe('diagram node form primary identity', () => {
  it('preserves the declared primary key so the case slice stays visible', () => {
    expect(build(['dossier_key']).visible).toEqual([{ key: 'a', dossier_ref: 'D-1', text: 'case note' }])
  })
  it('preserves field metadata when the primary key is declared there', () => {
    const { primary, node, visible } = build(undefined)
    expect(node.primaryTableBinding.fieldDefinitions).toEqual(primary.fieldDefinitions)
    expect(visible).toHaveLength(1)
  })
})
