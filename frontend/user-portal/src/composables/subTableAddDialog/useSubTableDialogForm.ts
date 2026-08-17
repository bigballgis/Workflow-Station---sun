import { ref, computed, watch, nextTick, type Ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  applyAuditFieldDefaults,
  applyEditAuditDefaults,
  buildInitialRow,
  buildRules,
  mergeFormRowWithSeed,
  isAuditField,
} from '@/components/subTableAddDialogHelpers'
import type { DialogColumn } from '@/components/subTableAddDialogHelpers'
import type { RowFormulaRule, ValidationRule } from '@/components/formRendererHelpers'
import { evaluateFormula, validateField } from '@/components/businessLogicEngine'
import { materializeFormCreateValidationRules } from '@/utils/formCreateValidateRules'
import { collectComputedColumns, previewComputedRow } from '@/utils/computedFieldRuntime'
import type { BindingFieldDefinition } from '@/utils/subTableRowRuntime'

/** i18n translate signature (kept loose to match the SFC's useI18n usage). */
type DialogT = (key: string) => string

/** Props subset the form orchestrator depends on (mirrors SFC defineProps). */
interface FormProps {
  visible: boolean
  columns: DialogColumn[]
  /** List-view columns used to auto-fill audit fields on save (may include fields hidden from the dialog). */
  auditColumns?: DialogColumn[]
  mode: 'add' | 'edit'
  initialData?: Record<string, any>
  rowFormulas?: RowFormulaRule[]
  /** This table's own columns — supplies the computed (formula) column definitions. */
  fieldDefinitions?: BindingFieldDefinition[]
  /** MAIN form values so a SUB-table formula can preview {@code table.column}. */
  hostPrimaryFormData?: Record<string, unknown>
  columnValidationRules?: Record<string, ValidationRule[]>
  saveRow?: (row: Record<string, unknown>) => void | Promise<void>
}

/** Emit signature subset the form orchestrator depends on (mirrors SFC defineEmits). */
interface FormEmit {
  (e: 'update:visible', val: boolean): void
  (e: 'save', rowData: Record<string, any>): void
}

/** Side-effect hooks owned by sibling composables, injected to break cycles. */
interface FormDeps {
  formData: Ref<Record<string, any>>
  resetUploadNames: () => void
  backfillUploadNames: () => void
  resetLookupState: () => void
  destroyEditors: () => void
  fetchDepartmentTree: () => void
  /** Clear api.hidden / api.display state from prior dialog open. */
  resetDialogEventVisibility?: () => void
  /** Run Form-level onCreated → onMounted after row model is ready. */
  bootstrapDialogFormLifecycle?: () => void
  /** Form onReload when dialog re-inits while still visible. */
  runFormOnReload?: () => void
  /** Form beforeSubmit — return false to abort save. */
  runFormBeforeSubmit?: () => boolean
  /** Form onSubmit after validation, before persist. */
  runFormOnSubmit?: () => void
  /** Form onReset when dialog closes. */
  runFormOnReset?: () => void
}

/**
 * Core form orchestration for the sub-table add/edit dialog: model state, rules,
 * row-formula calculation (Task 8.6), column validation (Task 8.7), open/reset
 * lifecycle and save. Logic and ordering preserved verbatim from the original SFC.
 */
export function useSubTableDialogForm(props: FormProps, emit: FormEmit, t: DialogT, deps: FormDeps) {
  const {
    formData,
    resetUploadNames,
    backfillUploadNames,
    resetLookupState,
    destroyEditors,
    fetchDepartmentTree,
    resetDialogEventVisibility,
    bootstrapDialogFormLifecycle,
    runFormOnReload,
    runFormBeforeSubmit,
    runFormOnSubmit,
    runFormOnReset,
  } = deps

  const formRef = ref<FormInstance>()
  const saving = ref(false)
  const dialogKey = ref(0)

  /**
   * Prefer Form Design validate rules on columns; materialize deferred custom
   * {@code validator} scripts the same way FormRenderer does (needs live formData).
   */
  const formRules = computed<FormRules>(() => {
    const base = buildRules(props.columns)
    const out: FormRules = {}
    for (const [field, rules] of Object.entries(base)) {
      if (!Array.isArray(rules)) continue
      out[field] = materializeFormCreateValidationRules(
        rules as Array<Record<string, unknown>>,
        () => formData.value,
        () => props.columns.map((c) => ({ key: c.field, label: c.label })),
      ) as FormRules[string]
    }
    return out
  })


  // ─── Row formula calculation (Task 8.6) ─────────────────────────────────────
  const calculatedColumns = computed(() => {
    if (!props.rowFormulas?.length) return new Set<string>()
    return new Set(props.rowFormulas.map(f => f.targetColumn))
  })

  function isColDisabled(col: DialogColumn): boolean {
    return col.readonly === true || calculatedColumns.value.has(col.field) || isAuditField(col.field)
  }

  // Watch dependent column values and recompute target columns.
  // The getter returns a stable primitive (joined dep values) instead of a fresh object, so the
  // watch fires only when a dependency value actually changes — no { deep: true } traversal needed.
  watch(
    () => {
      if (!props.rowFormulas?.length) return ''
      const parts: string[] = []
      for (const formula of props.rowFormulas!) {
        for (const dep of formula.dependsOn) {
          parts.push(`${dep}=${String(formData.value[dep] ?? '')}`)
        }
      }
      return parts.join('|')
    },
    () => {
      if (!props.rowFormulas?.length) return
      for (const formula of props.rowFormulas!) {
        const fieldValues: Record<string, unknown> = {}
        for (const dep of formula.dependsOn) {
          fieldValues[dep] = formData.value[dep]
        }
        formData.value[formula.targetColumn] = evaluateFormula(formula.expression, fieldValues)
      }
    }
  )

  // ─── Computed (formula) columns from Table Design ───────────────────────────
  // Distinct from the rowFormulas above, which are mathjs expressions authored in Form Design.
  // These come from the table's own column definitions and the server recomputes them on write;
  // previewing here only keeps the dialog in step with what the save will produce.
  const computedColumns = computed(() => collectComputedColumns(props.fieldDefinitions))

  /** fieldName → error code for formulas the server would refuse to save. */
  const computedFieldErrors = ref<Record<string, string>>({})

  function parentRowsForPreview(): Record<string, Record<string, unknown>> {
    const primary = props.hostPrimaryFormData
    if (!primary) return {}
    const parents: Record<string, Record<string, unknown>> = {}
    for (const column of computedColumns.value) {
      for (const dep of column.definition?.dependsOn ?? []) {
        const dot = dep.indexOf('.')
        if (dot <= 0) continue
        parents[dep.slice(0, dot).toLowerCase()] = primary
      }
    }
    return parents
  }

  function recomputeComputedColumns(): void {
    if (!computedColumns.value.length) return
    // Row scope only: a sub-table row has no sub-tables of its own to aggregate over, and the
    // designer's aggregate formulas belong to the parent record, not to this row.
    const preview = previewComputedRow(
      computedColumns.value,
      formData.value,
      {},
      parentRowsForPreview(),
    )
    for (const [fieldName, value] of Object.entries(preview.values)) {
      formData.value[fieldName] = value
    }
    for (const fieldName of Object.keys(preview.errors)) {
      formData.value[fieldName] = null
    }
    computedFieldErrors.value = preview.errors
  }

  // Same stable-primitive getter as the rowFormulas watch above: returning the dependency values
  // joined into a string avoids a deep traversal of the whole row on every keystroke.
  watch(
    () => {
      if (!computedColumns.value.length) return ''
      const parts: string[] = []
      for (const column of computedColumns.value) {
        for (const dep of column.definition?.dependsOn ?? []) {
          const dot = dep.indexOf('.')
          if (dot <= 0) {
            parts.push(`${dep}=${String(formData.value[dep] ?? '')}`)
            continue
          }
          const columnName = dep.slice(dot + 1)
          parts.push(`${dep}=${String(props.hostPrimaryFormData?.[columnName] ?? '')}`)
        }
      }
      return parts.join('|')
    },
    () => { recomputeComputedColumns() },
  )

  // ─── Column validation errors (Task 8.7) ────────────────────────────────────
  const columnErrors = ref<Record<string, string[]>>({})

  function validateColumns(): boolean {
    columnErrors.value = {}
    if (!props.columnValidationRules) return true
    let allValid = true
    for (const [colName, rules] of Object.entries(props.columnValidationRules)) {
      const errors = validateField(formData.value[colName], rules)
      if (errors.length > 0) {
        columnErrors.value[colName] = errors
        allValid = false
      }
    }
    return allValid
  }

  function initDialogFormState(trigger: 'open' | 'data-change') {
    if (!props.visible) return
    // Force re-mount on open to reset internal control state.
    if (trigger === 'open') dialogKey.value += 1
    resetUploadNames()
    columnErrors.value = {}
    resetLookupState()
    resetDialogEventVisibility?.()
    // Fetch department tree if any column is of type 'department'
    if (props.columns.some(c => c.type === 'department')) {
      fetchDepartmentTree()
    }
    if (props.mode === 'edit' && props.initialData) {
      // Deep-clone to avoid mutating the original row
      formData.value = { ...buildInitialRow(props.columns), ...JSON.parse(JSON.stringify(props.initialData)) }
      // Back-fill upload file names from URL (prefer originalName query param if present)
      backfillUploadNames()
    } else {
      formData.value = props.initialData
        ? { ...buildInitialRow(props.columns), ...JSON.parse(JSON.stringify(props.initialData)) }
        : buildInitialRow(props.columns)
    }

    // Preview over the seeded row: an Add dialog has no server-computed value yet, and the watch
    // below only fires once a dependency changes.
    computedFieldErrors.value = {}
    recomputeComputedColumns()

    // Form-level onCreated/onMounted/onChange(__bootstrap__). Must run after model seed.
    bootstrapDialogFormLifecycle?.()
    // Re-init while open maps to form-create onReload (first open uses bootstrap only).
    if (trigger === 'data-change') runFormOnReload?.()

    // Element Plus Form keeps some per-field state; ensure each init starts clean.
    nextTick(() => {
      formRef.value?.clearValidate()
    })
  }

  // Initialise / reset form whenever dialog opens
  watch(
    () => props.visible,
    (open) => {
      if (!open) return
      initDialogFormState('open')
    },
    { immediate: false }
  )

  // Also re-init while visible if caller swaps initialData/mode without fully closing.
  watch(
    () => [props.mode, props.initialData] as const,
    () => {
      if (!props.visible) return
      initDialogFormState('data-change')
    },
    { deep: false }
  )

  function handleClose() {
    destroyEditors()
    // Form onReset while model still holds current values (form-create parity).
    runFormOnReset?.()
    // Avoid resetFields(): it resets to the first-mounted "initial model" snapshot and
    // can leak previous values between different row edits. We explicitly reset our model.
    resetUploadNames()
    columnErrors.value = {}
    resetLookupState()
    resetDialogEventVisibility?.()
    formData.value = buildInitialRow(props.columns)
    formRef.value?.clearValidate()
    emit('update:visible', false)
  }

  async function handleSave() {
    if (!formRef.value || saving.value) return
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) return
    // Run column validation rules (Task 8.7)
    if (!validateColumns()) return
    // A formula the server refuses to evaluate would make the whole write fail; the inline error
    // is already on the offending field, so stop here instead of round-tripping to a rejection.
    if (Object.keys(computedFieldErrors.value).length > 0) return
    if (runFormBeforeSubmit && runFormBeforeSubmit() === false) return
    const seed = props.mode === 'add' ? props.initialData : undefined
    const row = mergeFormRowWithSeed(seed, formData.value as Record<string, unknown>)
    // Audit fields are generated at real save time (never when the dialog opens):
    // add fills created_* + updated_*, edit refreshes updated_* only.
    if (props.mode === 'add') {
      applyAuditFieldDefaults(row, props.auditColumns ?? props.columns)
    } else {
      applyEditAuditDefaults(row, props.auditColumns ?? props.columns)
    }
    runFormOnSubmit?.()
    saving.value = true
    try {
      if (props.saveRow) {
        await props.saveRow(row)
      } else {
        emit('save', row)
      }
      emit('update:visible', false)
    } catch (e) {
      ElMessage.error(
        e instanceof Error && e.message ? e.message : t('common.operationFailed'),
      )
    } finally {
      saving.value = false
    }
  }

  return {
    formRef,
    saving,
    dialogKey,
    formRules,
    calculatedColumns,
    isColDisabled,
    columnErrors,
    computedFieldErrors,
    validateColumns,
    initDialogFormState,
    handleClose,
    handleSave,
  }
}
