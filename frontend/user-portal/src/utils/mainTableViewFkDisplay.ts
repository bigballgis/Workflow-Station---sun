/**
 * Match an FK scalar against same-process MAIN variables and pick a related attribute.
 * Mirrors backend {@code MainTableViewFkDisplaySupport} for portal hydration / unit tests.
 */
export function resolveFkDisplayAttribute(
  mainVars: Record<string, unknown> | null | undefined,
  fkValue: unknown,
  primaryKeyFields: string[] | null | undefined,
  displayField: string | null | undefined,
): unknown {
  if (!mainVars || fkValue == null || !displayField?.trim()) return undefined
  const fkScalar = scalarString(fkValue)
  if (!fkScalar) return undefined

  const pkFields = primaryKeyFields?.filter(f => !!f?.trim()) || []
  for (const pk of pkFields) {
    if (fkEquals(mainVars[pk], fkScalar)) {
      return mainVars[displayField]
    }
  }
  // FALLBACK(migration): try common id keys when PK meta is missing
  for (const candidate of ['id', 'id_idw']) {
    if (fkEquals(mainVars[candidate], fkScalar)) {
      return mainVars[displayField]
    }
  }
  return undefined
}

function scalarString(value: unknown): string | null {
  if (value == null) return null
  if (typeof value === 'object') return null
  const s = String(value).trim()
  return s || null
}

function fkEquals(mainPkValue: unknown, fkScalar: string): boolean {
  const main = scalarString(mainPkValue)
  return main != null && main === fkScalar
}
