import { computed, type Ref } from 'vue'
import { useSelectedAssignmentUsers } from '@platform-shared/batch/useSelectedAssignmentUsers'
import { extractUserIdFromCellValue } from '@/components/subTableAddDialogHelpers'
import { useBatchAssignmentDirectory } from './useBatchAssignmentDirectory'
import { resolveDisplayNameFromAssigneeCell } from './useSubTableAssignment'

/** General list cells follow the explicit BPMN field, not field-name heuristics. */
export function useSubTableAssignmentLabels(
  rows: Ref<Array<Record<string, unknown>>>,
  assigneeField: Ref<string | undefined>,
  t: (key: string) => string,
) {
  const directory = useBatchAssignmentDirectory(t)
  const ids = computed(() => {
    const field = assigneeField.value
    return field ? rows.value.map(row => extractUserIdFromCellValue(row[field])).filter(Boolean) : []
  })
  const { userOptionsFor } = useSelectedAssignmentUsers(directory, ids)
  function isAssignmentPersonColumn(field: string): boolean {
    return !!assigneeField.value && field === assigneeField.value
  }
  function assignmentPersonLabel(value: unknown): string {
    if (value == null || value === '') return '-'
    const id = extractUserIdFromCellValue(value)
    const option = userOptionsFor('general', id).find(user => user.id === id)
    // FALLBACK(ux): retain the stored identity while loading or when unavailable;
    // directory failures are also surfaced by the list's error alert.
    return option?.name || resolveDisplayNameFromAssigneeCell(value) || id || '-'
  }
  return { isAssignmentPersonColumn, assignmentPersonLabel, assignmentLabelError: directory.error }
}
