import { describe, expect, it } from 'vitest'
import { computed, ref } from 'vue'
import { useTableFieldRules } from '../useTableFieldRules'
import type { AssignmentConfig } from '@/utils/miAssignmentConfig'

const CONFIG: AssignmentConfig = {
  allowUser: true, allowRole: true,
  assigneeField: 'assignee', roleField: 'role_code', buField: 'bu_code',
}

function makeComposable() {
  return useTableFieldRules({
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
}

const LOOSE_FIELDS = [
  { field: 'name', type: 'input', title: 'Name' },
  { field: 'assignee', type: 'lookup', title: 'Assignee' },
  { field: 'bu_code', type: 'select', title: 'Business Unit' },
  { field: 'role_code', type: 'select', title: 'Role' },
]

/**
 * Deleting the Assignment Mode container on the canvas and the container never
 * having existed look identical in the rule tree (both: loose fields, no
 * container). Without a persisted "already handled" flag, both cases get
 * auto-re-wrapped on every load — a deliberate deletion could never stick.
 */
describe('buildEffectiveSubFormConfig — one-time Assignment Mode auto-adoption', () => {
  it('auto-adopts loose fields into a container on first load (legacy form, no flag yet)', () => {
    const { buildEffectiveSubFormConfig } = makeComposable()
    const result = buildEffectiveSubFormConfig(
      { 10: { rule: LOOSE_FIELDS, options: {} } },
      10,
      [{ id: 10, bindingType: 'SUB', bindingMode: 'EDITABLE', tableId: 1, sortOrder: 0 }],
      1,
    )
    expect(result.rule.some((r: any) => r.type === 'miAssignment')).toBe(true)
    expect(result.miAssignmentAdopted).toBe(true)
  })

  it('does NOT re-create the container once already adopted, even with loose fields present', () => {
    const { buildEffectiveSubFormConfig } = makeComposable()
    // Simulates: user deleted the container after a prior adopt+save — the fields
    // are loose again (or gone), but the persisted flag says "don't touch this again".
    const result = buildEffectiveSubFormConfig(
      { 10: { rule: LOOSE_FIELDS, options: {}, miAssignmentAdopted: true } },
      10,
      [{ id: 10, bindingType: 'SUB', bindingMode: 'EDITABLE', tableId: 1, sortOrder: 0 }],
      1,
    )
    expect(result.rule.some((r: any) => r.type === 'miAssignment')).toBe(false)
    expect(result.rule.map((r: any) => r.field)).toEqual(['name', 'assignee', 'bu_code', 'role_code'])
    expect(result.miAssignmentAdopted).toBe(true)
  })

  it('still re-wraps an existing, already-nested container idempotently once adopted', () => {
    const { buildEffectiveSubFormConfig } = makeComposable()
    const nested = [
      { field: 'name', type: 'input', title: 'Name' },
      {
        type: 'miAssignment',
        _miAdopted: true,
        children: [
          { field: 'assignee', type: 'lookup', title: 'Assignee' },
          { field: 'bu_code', type: 'select', title: 'Business Unit' },
          { field: 'role_code', type: 'select', title: 'Role' },
        ],
      },
    ]
    const result = buildEffectiveSubFormConfig(
      { 10: { rule: nested, options: {}, miAssignmentAdopted: true } },
      10,
      [{ id: 10, bindingType: 'SUB', bindingMode: 'EDITABLE', tableId: 1, sortOrder: 0 }],
      1,
    )
    expect(result.rule).toHaveLength(2)
    expect((result.rule[1] as any).type).toBe('miAssignment')
    expect((result.rule[1] as any).children).toHaveLength(3)
    expect(result.miAssignmentAdopted).toBe(true)
  })

  it('preserves the unset flag when there is nothing to adopt from (no rule, no fields)', () => {
    const { buildEffectiveSubFormConfig } = makeComposable()
    const result = buildEffectiveSubFormConfig(
      { 10: { rule: [], options: {} } },
      10,
      [{ id: 10, bindingType: 'SUB', bindingMode: 'EDITABLE', tableId: 1, sortOrder: 0 }],
      1,
    )
    expect(result.rule).toEqual([])
    expect(result.miAssignmentAdopted).toBe(false)
  })
})
