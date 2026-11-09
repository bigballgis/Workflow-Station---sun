/**
 * Where a business unit or business role is used (backend `ReferenceNotice`), shown before it is changed or deleted.
 * A notice only: nothing is refused because of it (platform access model §8.3).
 */
export interface ReferenceNotice {
  /** Function units whose access configuration, published processes or published views name it. */
  functionUnits: { code: string; name: string }[]
  /** Other references, one translated line per kind (e.g. "BI dashboard assignments: 2"). */
  others: string[]
}

export function hasReferences(notice: ReferenceNotice | null | undefined): boolean {
  return !!notice && (notice.functionUnits.length > 0 || notice.others.length > 0)
}
