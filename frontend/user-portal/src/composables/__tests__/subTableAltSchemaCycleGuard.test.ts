/**
 * Regression: RangeError "Maximum call stack size exceeded" when loading FU content
 * on task/application detail pages.
 *
 * Two forms in one FU can bind the same physical table under different binding ids
 * (copied BPMN forms — e.g. ATM Demo's PROCESS + TASK forms both bind ATM_transation).
 * When a binding derives no columns locally, deriveColumnsFromBinding resolves an
 * "alt" schema by tableId and recurses. resolveSubTableSchemaByTableId used to exclude
 * only the immediate binding id, so two such bindings resolved each other forever:
 * A (excl A) -> B, B (excl B) -> A, ... The visited-set guard must terminate this.
 *
 * The zero-column-but-has-schema state is real: a subForm whose rule holds only a
 * nested subTable node passes formHasSubTableSchemaForBinding but every rule is
 * filtered out by isDialogMappableSubFormRule (nested sub-table demo shape).
 */
import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { createTaskDetailFormSchema } from '../taskDetail/useTaskDetailFormSchema'
import { createApplicationDetailColumns } from '../applicationDetail/useApplicationDetailColumns'

const TABLE_ID = 77

/** subForm rule with no dialog-mappable field: only a nested subTable widget. */
function unmappableSubFormRule(nestedBindingId: number) {
  return [{ type: 'subTable', props: { _bindingId: nestedBindingId } }]
}

function makeForm(bindingId: number, nestedBindingId: number) {
  return {
    name: `form-${bindingId}`,
    data: JSON.stringify({
      rule: [],
      subForms: { [String(bindingId)]: { rule: unmappableSubFormRule(nestedBindingId) } },
      subListViews: {},
    }),
    tableBindings: [
      { bindingId, bindingType: 'SUB', tableId: TABLE_ID, tableName: 'shared_table' },
    ],
  }
}

// Form A (binding 1) and form B (binding 2) bind the same table; each has "schema"
// (non-empty subForm rule) that derives zero columns -> mutual alt resolution.
const cyclicForms = [makeForm(1, 9001), makeForm(2, 9002)]

function makeCtx(): any {
  return {
    lookupDbConfigs: ref({}),
    relationViewConfigs: ref({}),
    cachedContentForms: cyclicForms,
    cachedRelationTableFieldIndex: new Map(),
    extractFieldsRecursive: (items: any[]) => items,
  }
}

describe('sub-table alt-schema resolution cycle guard', () => {
  it('taskDetail deriveColumnsFromBinding terminates on mutually-referencing bindings', () => {
    const { deriveColumnsFromBinding } = createTaskDetailFormSchema(makeCtx())
    for (const form of cyclicForms) {
      const cfg = JSON.parse(form.data)
      for (const b of form.tableBindings) {
        expect(() => deriveColumnsFromBinding(b, cfg.subForms, cfg)).not.toThrow()
      }
    }
  })

  it('applicationDetail deriveColumnsFromBinding terminates on mutually-referencing bindings', () => {
    const { deriveColumnsFromBinding } = createApplicationDetailColumns(makeCtx())
    for (const form of cyclicForms) {
      const cfg = JSON.parse(form.data)
      for (const b of form.tableBindings) {
        expect(() => deriveColumnsFromBinding(b, cfg)).not.toThrow()
      }
    }
  })

  it('applicationDetail resolveSubTableBindingColumnsForPortal terminates and still derives alt columns when one form has real schema', () => {
    // Binding 2's form now has a designed sub-form field; binding 1 (zero local columns)
    // must resolve it through the alt hop without recursing back into binding 1.
    const formsWithSchema = [
      makeForm(1, 9001),
      {
        name: 'form-2',
        data: JSON.stringify({
          rule: [],
          subForms: {
            '2': { rule: [{ type: 'input', field: 'amount', title: 'Amount' }] },
          },
          subListViews: {},
        }),
        tableBindings: [
          { bindingId: 2, bindingType: 'SUB', tableId: TABLE_ID, tableName: 'shared_table' },
        ],
      },
    ]
    const ctx = makeCtx()
    ctx.cachedContentForms = formsWithSchema
    const { resolveSubTableBindingColumnsForPortal } = createApplicationDetailColumns(ctx)
    const cfg1 = JSON.parse(formsWithSchema[0].data)
    const cols = resolveSubTableBindingColumnsForPortal(
      formsWithSchema[0].tableBindings[0],
      cfg1,
      formsWithSchema,
    )
    expect(cols.map(c => c.field)).toContain('amount')
  })
})
