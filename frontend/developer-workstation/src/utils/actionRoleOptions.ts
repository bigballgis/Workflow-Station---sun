export interface ActionRoleOption {
  code: string
  name: string
}

/** Catalog roles plus any saved codes that are no longer in the catalog. */
export function mergeActionRoleOptions(
  catalog: readonly ActionRoleOption[],
  selectedCodes: readonly string[],
): ActionRoleOption[] {
  const byCode = new Map<string, ActionRoleOption>()
  for (const role of catalog) {
    if (role.code) byCode.set(role.code, role)
  }
  for (const code of selectedCodes) {
    const trimmed = code.trim()
    if (trimmed && !byCode.has(trimmed)) {
      byCode.set(trimmed, { code: trimmed, name: trimmed })
    }
  }
  return [...byCode.values()]
}
