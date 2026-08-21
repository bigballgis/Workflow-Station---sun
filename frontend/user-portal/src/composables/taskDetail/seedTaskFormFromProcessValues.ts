/**
 * When the task form includes the same field keys as the Process Form, seed empty
 * task formData slots from processFormValues (e.g. LOOKUP cascade autofill written
 * at Start Process). Without this, To Do shows empty readonly tags even though
 * processFormRef.fieldValues already holds the filled rows.
 *
 * Call this after MI isolate as well: isolateMiSubTaskData prefers participant-row
 * slots and can overwrite Start Process LOOKUP values with null/[] when the MI row
 * has empty keys of the same name.
 */
export function isEmptySeedableFormValue(value: unknown): boolean {
  return (
    value === undefined ||
    value === null ||
    value === '' ||
    (Array.isArray(value) && value.length === 0)
  )
}

export function seedTaskFormFromProcessValues(
  formData: Record<string, unknown>,
  processValues: Record<string, unknown> | null | undefined,
  fieldKeys: string[],
): { next: Record<string, unknown>; patched: boolean } {
  if (!processValues || typeof processValues !== 'object' || !fieldKeys.length) {
    return { next: formData, patched: false }
  }
  let patched = false
  const next: Record<string, unknown> = { ...formData }
  for (const key of fieldKeys) {
    if (!key || key.startsWith('__')) continue
    if (!Object.prototype.hasOwnProperty.call(processValues, key)) continue
    const fromProcess = processValues[key]
    if (isEmptySeedableFormValue(fromProcess)) continue
    if (!isEmptySeedableFormValue(next[key])) continue
    next[key] = fromProcess
    patched = true
    const displayKey = `${key}__display`
    if (isEmptySeedableFormValue(next[displayKey]) && !isEmptySeedableFormValue(processValues[displayKey])) {
      next[displayKey] = processValues[displayKey]
    }
  }
  for (const key of fieldKeys) {
    if (!key || key.startsWith('__')) continue
    const displayKey = `${key}__display`
    if (isEmptySeedableFormValue(next[displayKey]) && !isEmptySeedableFormValue(processValues[displayKey])) {
      next[displayKey] = processValues[displayKey]
      patched = true
    }
  }
  return { next, patched }
}
