import { describe, expect, it } from 'vitest'
import { computed, ref } from 'vue'
import type { FieldDefinition, FormDefinition } from '@/api/functionUnit'
import { useTableFieldRules } from '../useTableFieldRules'
import type { AssignmentConfig } from '@/utils/miAssignmentConfig'

const CONFIG: AssignmentConfig = {
  allowUser: true, allowRole: true,
  assigneeField: 'assignee', roleField: 'role_code', buField: 'bu_code',
}

function auditField(fieldName: string, dataType: FieldDefinition['dataType']): FieldDefinition {
  return {
    fieldName,
    dataType,
    nullable: true,
    isPrimaryKey: false,
    displayName: fieldName,
  }
}

describe('useTableFieldRules — audit field import', () => {
  it('maps explicitly selected audit fields to readonly canvas rules', () => {
    const { mapFieldsToFormRules } = useTableFieldRules({
      store: { tables: [] },
      selectedForm: ref(null),
      designerRef: ref(null),
      subDesignerRefs: ref([]),
      designerSubBindings: computed(() => []),
      activeDesignerTab: ref('main'),
      getActiveDesignerRef: () => null,
      defaultFormOption: computed(() => ({})),
      getAssignmentConfig: () => CONFIG,
      t: (key: string) => key,
    })

    const rules = mapFieldsToFormRules([
      auditField('created_at', 'TIMESTAMP'),
      auditField('created_by', 'VARCHAR'),
      auditField('updated_at', 'TIMESTAMP'),
      auditField('updated_by', 'VARCHAR'),
    ]) as Array<Record<string, unknown>>

    expect(rules.map(r => r.field)).toEqual([
      'created_at', 'created_by', 'updated_at', 'updated_by',
    ])
    for (const rule of rules) {
      expect(rule.readonly).toBe(true)
      expect((rule.props as Record<string, unknown>).readonly).toBe(true)
    }
    expect(rules[0].type).toBe('datePicker')
    expect(rules[1].type).toBe('input')
  })

  it('does not auto-fill audit fields when building an empty form from the table', () => {
    const { buildEffectiveMainFormConfig } = useTableFieldRules({
      store: {
        tables: [{
          id: 1,
          fieldDefinitions: [
            auditField('title', 'VARCHAR'),
            auditField('created_at', 'TIMESTAMP'),
            auditField('updated_by', 'VARCHAR'),
          ],
        }],
      },
      selectedForm: ref(null),
      designerRef: ref(null),
      subDesignerRefs: ref([]),
      designerSubBindings: computed(() => []),
      activeDesignerTab: ref('main'),
      getActiveDesignerRef: () => null,
      defaultFormOption: computed(() => ({})),
      getAssignmentConfig: () => CONFIG,
      t: (key: string) => key,
    })

    const result = buildEffectiveMainFormConfig(
      { configJson: { rule: [] } } as FormDefinition,
      [{ bindingType: 'PRIMARY', tableId: 1 }],
    )
    expect((result.rule as Array<{ field: string }>).map(r => r.field)).toEqual(['title'])
  })
})
