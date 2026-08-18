/**
 * Live preview of the main form's computed columns.
 *
 * The value that lands in the record is always the server's — {@code ComputedFieldRecalculator}
 * recomputes every computed column on write. This recomputes the same formulas in the browser so
 * the number moves as the user types, on exactly the triggers that can change an input: a field
 * edit, and a sub-table row change for aggregates.
 *
 * An aggregate whose sub-table is not on this screen is left alone rather than computed over the
 * rows that happen to be loaded, so a partially loaded form keeps showing the server's value.
 */
import { computed, ref, type Ref } from 'vue'
import {
  collectComputedColumns,
  previewComputedRow,
  type ComputedColumn,
} from '../../utils/computedFieldRuntime'
import type { BindingFieldDefinition } from '../../utils/subTableRowRuntime'

interface AggregateSourceBinding {
  bindingId?: number
  tableName?: string
  physicalTableName?: string
  data?: unknown[]
}

interface ComputedFieldsDeps {
  /** Field definitions of the PRIMARY table binding — the columns the main form writes. */
  primaryFieldDefinitions: () => BindingFieldDefinition[] | undefined
  /** Sub-table bindings rendered on this form; supplies the rows aggregates read. */
  subTableBindings: () => AggregateSourceBinding[] | undefined
  formData: Ref<Record<string, any>>
}

export function useComputedFields(deps: ComputedFieldsDeps) {
  const computedColumns = computed<ComputedColumn[]>(() =>
    collectComputedColumns(deps.primaryFieldDefinitions()),
  )

  const hasComputedFields = computed(() => computedColumns.value.length > 0)

  /** fieldName → error code for formulas the server would refuse to save. */
  const computedFieldErrors = ref<Record<string, string>>({})

  /**
   * Rows the form has emitted but not yet seen echoed back through props. A sub-table edit reaches
   * the binding only after the parent re-renders, and an aggregate that waited for that round trip
   * would lag one edit behind the row the user just changed.
   */
  const pendingRowsByBinding = new Map<number, unknown[]>()

  function asRows(value: unknown): Array<Record<string, unknown>> {
    if (!Array.isArray(value)) return []
    return value.filter(
      (row): row is Record<string, unknown> =>
        row !== null && typeof row === 'object' && !Array.isArray(row),
    )
  }

  /**
   * Sub-table rows keyed by physical table name — the same name a formula's aggregate refers to,
   * since computed fields are authored in Table Design where only physical names exist.
   */
  function loadedSubTableRows(): Record<string, Array<Record<string, unknown>>> {
    const rows: Record<string, Array<Record<string, unknown>>> = {}
    for (const binding of deps.subTableBindings() ?? []) {
      const name = (binding.physicalTableName ?? binding.tableName ?? '').trim().toLowerCase()
      if (!name) continue
      const pending = binding.bindingId != null
        ? pendingRowsByBinding.get(Number(binding.bindingId))
        : undefined
      const source = pending ?? binding.data
      if (!Array.isArray(source)) continue
      rows[name] = asRows(source)
    }
    return rows
  }

  /**
   * @param changedSubTable rows just emitted for a binding, before props echo them back.
   */
  function recomputeComputedFields(changedSubTable?: { bindingId: number; rows: unknown[] }): void {
    if (changedSubTable) {
      pendingRowsByBinding.set(Number(changedSubTable.bindingId), changedSubTable.rows)
    }
    if (!hasComputedFields.value) return
    const preview = previewComputedRow(
      computedColumns.value,
      deps.formData.value,
      loadedSubTableRows(),
    )
    for (const [fieldName, value] of Object.entries(preview.values)) {
      deps.formData.value[fieldName] = value
    }
    // A failing formula clears its field rather than leaving the previous result on screen: that
    // number no longer follows from the inputs, and the save it belongs to is going to be rejected.
    for (const fieldName of Object.keys(preview.errors)) {
      deps.formData.value[fieldName] = null
    }
    computedFieldErrors.value = preview.errors
  }

  return {
    computedColumns,
    hasComputedFields,
    computedFieldErrors,
    recomputeComputedFields,
  }
}
