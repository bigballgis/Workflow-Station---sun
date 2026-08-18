import { describe, it, expect } from 'vitest'
import { computed } from 'vue'
import { useSubTablePortalViews } from '../useSubTablePortalViews'
import type { FormField } from '@/components/formRendererHelpers'
import type { SubTableBinding } from '../useSubTableBindings'

function binding(
  bindingId: number,
  extras: Partial<SubTableBinding> = {},
): SubTableBinding {
  return {
    bindingId,
    bindingType: 'SUB',
    bindingMode: 'EDITABLE',
    tableName: `t${bindingId}`,
    tableType: 'SUB',
    tableDescription: '',
    columns: [],
    data: [],
    formOptions: { onChange: `own-${bindingId}` },
    ...extras,
  }
}

function placedSubTable(bindingId: number, sourceType: 'subForm' | 'linkForm'): FormField {
  return {
    key: `__subTable_${bindingId}`,
    label: '',
    type: 'subTable',
    _bindingId: bindingId,
    portalViews: {
      assigneeTodo: 'formBelowTable',
      initiatorRequest: 'mirrorTodo',
      assigneeTodoFormSource: { type: sourceType, formId: null, linkFormColumnId: null },
    },
  }
}

function createApi(bindings: SubTableBinding[]) {
  const map = new Map(bindings.map(b => [b.bindingId, b]))
  return useSubTablePortalViews({
    viewContext: () => 'assigneeTodo',
    nativeSubTableBindingIds: () => bindings.map(b => b.bindingId),
    formConfig: () => ({}),
    readonly: () => false,
    resolveBinding: (id?: number) => (id == null ? undefined : map.get(id)),
    linkableSubTableBindings: computed(() => bindings),
    isBindingModeEditable: mode => mode === 'EDITABLE',
  })
}

describe('resolveInlineFormSourceBinding — form-below-table Event source', () => {
  const own = binding(10, {
    columns: [
      {
        type: 'linkForm',
        props: { componentId: 'lf-1', boundSubTableBindingId: 20 },
      },
    ],
  })
  const target = binding(20, { formOptions: { onChange: 'target-20' } })

  it('keeps the placed binding when designer source is subForm even if a Link Form column exists', () => {
    const api = createApi([own, target])
    const src = api.resolveInlineFormSourceBinding(placedSubTable(10, 'subForm'))
    expect(src?.bindingId).toBe(10)
    expect(src?.formOptions?.onChange).toBe('own-10')
  })

  it('switches to the Link Form target only when designer source is linkForm', () => {
    const api = createApi([own, target])
    const src = api.resolveInlineFormSourceBinding(placedSubTable(10, 'linkForm'))
    expect(src?.bindingId).toBe(20)
    expect(src?.formOptions?.onChange).toBe('target-20')
  })
})
