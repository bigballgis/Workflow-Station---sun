import { describe, expect, it, vi } from 'vitest'
import { computed, ref } from 'vue'
import type { FormDefinition } from '@/api/functionUnit'

vi.mock('element-plus', () => ({
  ElMessage: {
    warning: vi.fn(),
    error: vi.fn(),
    success: vi.fn(),
    info: vi.fn(),
  },
}))

import { useFormPreviewBuild } from '../useFormPreviewBuild'

const ACTION_RULE = [
  { type: 'input', field: 'id', title: 'Id', props: {} },
  { type: 'input', field: 'main_id', title: 'main id', props: {} },
  { type: 'input', field: 'remark_type', title: 'Remark Type', props: {} },
  { type: 'input', field: 'remark_content', title: 'Remark Content', props: {} },
]

const DIRTY_TOP_LEVEL = [
  { type: 'input', field: 'I', title: 'Meeting Name', props: {} },
]

function createHarness(form: FormDefinition, liveTopRule: unknown[] = []) {
  const selectedForm = ref(form)
  const designerRef = ref({
    getRule: () => liveTopRule,
    getOption: () => ({}),
  })
  const actionBinding = (form.tableBindings ?? []).find(b => b.bindingType === 'ACTION')
  return {
    selectedForm,
    ...useFormPreviewBuild({
      functionUnitId: 1,
      store: {
        tables: [
          {
            id: 30,
            tableName: 'action_table',
            tableDisplayName: 'Action Table',
            tableType: 'ACTION',
            fieldDefinitions: [
              { fieldName: 'id', dataType: 'VARCHAR' },
              { fieldName: 'remark_type', dataType: 'VARCHAR' },
            ],
          },
          {
            id: 50,
            tableName: 'main_table',
            tableDisplayName: 'Main Table',
            tableType: 'MAIN',
            fieldDefinitions: [{ fieldName: 'I', dataType: 'VARCHAR' }],
          },
        ],
        fetchTables: async () => undefined,
      },
      selectedForm,
      designerRef,
      subDesignerRefs: ref([]),
      subFormCache: ref({}),
      designerSubBindings: computed(() => (
        actionBinding?.id != null
          ? [{ bindingId: actionBinding.id, tableId: actionBinding.tableId }]
          : []
      )),
      getActiveDesignerRef: () => designerRef.value,
      getTableFieldDefinitions: () => [],
      getPrimaryBindingFieldDefinitions: () => [],
      toSubTablePreviewColumns: () => [],
      makeLookupPreviewItem: (ruleItem: { field?: string }) => ({
        kind: 'lookup' as const,
        rule: ruleItem,
        field: ruleItem.field,
      }),
      getTableName: () => 'Action Table',
      getAssignmentConfig: () => undefined,
      t: (key: string) => key,
    }),
  }
}

describe('useFormPreviewBuild ACTION canvas', () => {
  it('builds preview items from ACTION subForms when top-level rule is empty', async () => {
    const { handlePreview, previewItems, previewBuilding } = createHarness({
      id: 10,
      formName: 'Popup Form',
      formType: 'ACTION',
      configJson: {
        rule: [],
        subForms: { '20': { rule: ACTION_RULE, options: {} } },
        options: {},
      },
      tableBindings: [
        { id: 20, tableId: 30, bindingType: 'ACTION', bindingMode: 'EDITABLE', tableName: 'action_table', sortOrder: 1 },
      ],
    })

    await handlePreview()
    expect(previewBuilding.value).toBe(false)
    const titles = JSON.stringify(previewItems.value)
    expect(titles).toContain('Remark Type')
    expect(titles).toContain('Remark Content')
  })

  it('keeps ACTION canvas fields when top-level rule is a dirty main-table snapshot', async () => {
    const { handlePreview, previewItems, previewPrimaryTableId } = createHarness({
      id: 10,
      formName: 'Popup Form',
      formType: 'ACTION',
      configJson: {
        rule: DIRTY_TOP_LEVEL,
        subForms: { '20': { rule: ACTION_RULE, options: {} } },
        options: {},
      },
      tableBindings: [
        { id: 40, tableId: 50, bindingType: 'PRIMARY', bindingMode: 'EDITABLE', tableName: 'main_table', sortOrder: 0 },
        { id: 20, tableId: 30, bindingType: 'ACTION', bindingMode: 'EDITABLE', tableName: 'action_table', sortOrder: 1 },
      ],
    }, DIRTY_TOP_LEVEL)

    await handlePreview()
    const titles = JSON.stringify(previewItems.value)
    expect(titles).toContain('Remark Type')
    expect(titles).not.toContain('Meeting Name')
    expect(previewPrimaryTableId.value).toBe(30)
  })
})
