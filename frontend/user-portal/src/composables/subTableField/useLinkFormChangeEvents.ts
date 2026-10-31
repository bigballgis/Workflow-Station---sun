import type { Ref } from 'vue'
import { isDisplayOnlyLayoutField, type FormField } from '@/components/formRendererHelpers'
import { useSubTableDialogComponentEvents, type DialogColumnWithEvents } from '@/composables/subTableAddDialog/useSubTableDialogComponentEvents'

function eventColumns(fields: FormField[]): DialogColumnWithEvents[] {
  return fields.flatMap(field => {
    const children = [...(field.children ?? []), ...(field.tabs?.flatMap(tab => tab.fields) ?? []),
      ...(field.collapsePanels?.flatMap(panel => panel.fields) ?? [])]
    if (children.length) return eventColumns(children)
    return isDisplayOnlyLayoutField(field) ? [] : [{ field: field.key, label: field.label,
      type: field.type, hidden: field.hidden, sourceRule: field.sourceRule }]
  })
}

/** Details edits use the same field change interpreter and current-row API as Edit. */
export function useLinkFormChangeEvents(
  model: Ref<Record<string, unknown>>,
  fields: () => FormField[],
  options: () => Record<string, unknown> | null | undefined,
): ReturnType<typeof useSubTableDialogComponentEvents> {
  return useSubTableDialogComponentEvents(model, () => eventColumns(fields()), options)
}
