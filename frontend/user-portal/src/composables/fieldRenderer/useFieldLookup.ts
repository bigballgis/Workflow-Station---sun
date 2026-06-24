// ---------------------------------------------------------------------------
// FieldRenderer — lookup backfill state (mirrors FormRenderer's
// lookupSelectedData / lookupLoadedViewFields) so the LookupViewDisplay backfill
// panel shows up inside SubTableInlineForm / Link Form modal / SubTaskForm
// (which all go through FieldRenderer, not FormRenderer).
// Behaviour copied verbatim from FieldRenderer.vue. Registers the modelValue
// watch first so the watcher registration order matches the original SFC.
// ---------------------------------------------------------------------------
import { ref, computed, watch } from 'vue'
import type { FieldRendererProps } from './types'

export function useFieldLookup(props: FieldRendererProps) {
  const lookupSelectedRow = ref<Record<string, any> | null>(null)
  const lookupLoadedViewFields = ref<any[]>([])

  const lookupShowBackfillView = computed<boolean>(() => {
    if (props.field.type !== 'lookup') return false
    return (props.field as any)._lookupShowBackfillView !== false
  })

  const effectiveLookupViewFields = computed(() => {
    const configured = (props.field as any)._lookupViewFields
    if (Array.isArray(configured) && configured.length > 0) return configured
    return lookupLoadedViewFields.value
  })

  function onLookupSelect(row: Record<string, any>) {
    lookupSelectedRow.value = row && typeof row === 'object' ? row : null
  }

  function onLookupClear() {
    lookupSelectedRow.value = null
  }

  function onLookupViewFieldsLoaded(vfs: any[]) {
    lookupLoadedViewFields.value = Array.isArray(vfs) ? vfs : []
  }

  // Lookup value changes by wholesale replacement; this only maps the value-as-a-whole to
  // lookupSelectedRow, so a shallow (reference) watch is enough — no deep traversal needed.
  watch(
    () => [props.modelValue, props.field?.type] as const,
    ([val, type]) => {
      if (type !== 'lookup') {
        lookupSelectedRow.value = null
        return
      }
      if (val && typeof val === 'object' && !Array.isArray(val) && Object.keys(val).length > 0) {
        lookupSelectedRow.value = val as Record<string, any>
      } else if (val == null || val === '') {
        lookupSelectedRow.value = null
      }
    },
    { immediate: true },
  )

  return {
    lookupSelectedRow,
    lookupShowBackfillView,
    effectiveLookupViewFields,
    onLookupSelect,
    onLookupClear,
    onLookupViewFieldsLoaded,
  }
}
