/**
 * Running instances must load the catalog package pinned at start, not the latest
 * enabled version of the same process key.
 */
export function pinnedCatalogContentRef(source: {
  functionUnitCatalogId?: string | null
  processDefinitionKey?: string | null
}): string | null {
  const pin = source.functionUnitCatalogId?.trim()
  if (pin) {
    return pin
  }
  const key = source.processDefinitionKey?.trim()
  return key || null
}
