import { defineComponent, h, type CSSProperties, type PropType, type VNodeChild } from 'vue'
import { ElCard, ElCol, ElCollapse, ElCollapseItem, ElRow, ElTabPane, ElTabs } from 'element-plus'
import FieldRenderer from './FieldRenderer.vue'
import { isDisplayOnlyLayoutField, isFormCreateRuleHidden, type FormField } from './formRendererHelpers'
import type { DialogColumn } from './subTableAddDialogHelpers'

/** Keeps the authored layout tree while the row dialog retains its existing field and save runtime. */
export default defineComponent({
  name: 'SubTableDialogLayout',
  props: {
    fields: Array as PropType<FormField[]>,
    columns: { type: Array as PropType<DialogColumn[]>, required: true },
    extraFields: { type: Array as PropType<FormField[]>, default: () => [] },
    assignmentFields: { type: Array as PropType<string[]>, default: () => [] },
  },
  setup(props, { slots }) {
    return () => {
      const columns = new Map(props.columns.map(column => [column.field, column]))
      const roots: FormField[] = props.fields?.length ? [...props.fields] : props.columns.map(column => ({ key: column.field, label: column.label, type: column.type ?? 'text' }))
      const children = (field: FormField): FormField[] => [
        ...(field.children ?? []), ...(field.tabs?.flatMap(tab => tab.fields) ?? []),
        ...(field.collapsePanels?.flatMap(panel => panel.fields) ?? []),
      ]
      const identity = (field: FormField) => field.type === 'subTable' ? `subTable:${field._bindingId}` : field.key
      const placed = new Set<string>()
      const index = (fields: FormField[]) => fields.forEach(field => { placed.add(identity(field)); index(children(field)) })
      index(roots)
      roots.push(...props.extraFields.filter(field => !placed.has(identity(field))))
      const legacyOwned = new Set<string>()
      const findLegacy = (fields: FormField[]) => fields.forEach(field => {
        if (field.type === 'miAssignment' && !field.children?.length) props.assignmentFields.forEach(key => legacyOwned.add(key))
        findLegacy(children(field))
      })
      findLegacy(roots)
      const visibleKeys = (fields: FormField[]): string[] => fields.flatMap(field => {
        if (field.hidden || (field.sourceRule && isFormCreateRuleHidden(field.sourceRule))) return []
        return columns.has(field.key) ? [field.key] : visibleKeys(children(field))
      })
      const grid = (fields: FormField[], assignmentLast?: string, readonly = false): VNodeChild =>
        h(ElRow, { gutter: 0, style: { width: '100%', margin: 0 } }, () => fields.map(field => render(field, assignmentLast, readonly)))
      const render = (field: FormField, assignmentLast?: string, readonly = false): VNodeChild => {
        if (field.hidden || (field.sourceRule && isFormCreateRuleHidden(field.sourceRule))) return null
        const source = field.sourceRule
        const style = source?.style as CSSProperties | undefined
        let content: VNodeChild
        if (field.type === 'row') {
          content = h(ElRow, { gutter: field.gutter ?? 20, style }, () => (field.children ?? []).map(child => render(child, assignmentLast, readonly)))
        } else if (field.type === 'col') {
          content = grid(field.children ?? [], assignmentLast, readonly)
        } else if (field.type === 'card') {
          content = h(ElCard, { ...(source?.props ?? {}), class: 'sub-table-dialog-card', style }, {
            ...(field.label ? { header: () => field.label } : {}),
            default: () => grid(field.children ?? [], assignmentLast, readonly),
          })
        } else if (field.type === 'tabs') {
          content = h(ElTabs, { modelValue: field.tabs?.[0]?.name, ...(source?.props ?? {}), style }, () => field.tabs?.map(tab => h(ElTabPane, { name: tab.name, label: tab.label }, () => grid(tab.fields, assignmentLast, readonly))))
        } else if (field.type === 'collapse') {
          content = h(ElCollapse, { ...(source?.props ?? {}), style }, () => field.collapsePanels?.map(panel => h(ElCollapseItem, { name: panel.name, title: panel.label }, () => grid(panel.fields, assignmentLast, readonly))))
        } else if (field.type === 'miAssignment') {
          const owned = field.children?.length ? field.children : props.assignmentFields.filter(key => columns.has(key)).map(key => ({ key, label: '', type: 'text' }))
          const last = visibleKeys(owned).at(-1)
          content = [slots.default?.({ item: { type: 'miAssignment', key: field.key } }), grid(owned, last, readonly || field.readonly === true)]
        } else if (field.type === 'subTable' || field.type === 'recordNote') {
          content = slots.placement?.({ field })
        } else if (isDisplayOnlyLayoutField(field)) {
          content = h(FieldRenderer, { field, readonly: true, modelValue: undefined })
        } else {
          if (legacyOwned.has(field.key) && !assignmentLast) return null
          const column = columns.get(field.key)
          if (!column) return null
          content = slots.default?.({ item: {
            type: 'column', key: field.key, column: readonly ? { ...column, readonly: true } : column,
            ...(assignmentLast ? { assignmentSlot: field.key === assignmentLast ? 'last' : 'owned' } : {}),
          } })
        }
        return h(ElCol, { key: field.key, span: field.span ?? 24, style: { minWidth: 0 } }, () => content)
      }
      return grid(roots)
    }
  },
})
