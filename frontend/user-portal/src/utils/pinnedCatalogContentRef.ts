/**
 * Running instances must load the catalog package pinned at start, never the latest
 * enabled version of the same process key: an instance without its pin has no content
 * to load (null), which callers report as a version error.
 */
export function pinnedCatalogContentRef(source: {
  functionUnitCatalogId?: string | null
}): string | null {
  return source.functionUnitCatalogId?.trim() || null
}
