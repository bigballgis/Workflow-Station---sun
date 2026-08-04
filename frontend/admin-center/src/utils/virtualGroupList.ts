import type { VirtualGroup } from '@/api/virtualGroup'

export type VirtualGroupTab = 'SYSTEM' | 'CUSTOM' | 'DEVELOPER'

export function filterVirtualGroupsByType(
  groups: VirtualGroup[],
  type: VirtualGroupTab
): VirtualGroup[] {
  return groups.filter((g) => g.type === type)
}

export function filterVirtualGroupsByKeyword(
  groups: VirtualGroup[],
  keyword: string
): VirtualGroup[] {
  const kw = keyword.trim().toLowerCase()
  if (!kw) return groups
  return groups.filter((g) => {
    const fields = [g.name, g.code, g.adGroup, g.boundRoleName, g.description]
    return fields.some((f) => (f || '').toLowerCase().includes(kw))
  })
}

/** Sort by name (case-insensitive); empty names last. */
export function sortVirtualGroupsByName(
  groups: VirtualGroup[],
  locale?: string
): VirtualGroup[] {
  return [...groups].sort((a, b) => {
    const an = (a.name || '').trim()
    const bn = (b.name || '').trim()
    if (!an && !bn) return 0
    if (!an) return 1
    if (!bn) return -1
    return an.localeCompare(bn, locale, { sensitivity: 'base' })
  })
}

export function filterSortVirtualGroups(
  groups: VirtualGroup[],
  type: VirtualGroupTab,
  keyword: string,
  locale?: string
): VirtualGroup[] {
  return sortVirtualGroupsByName(
    filterVirtualGroupsByKeyword(filterVirtualGroupsByType(groups, type), keyword),
    locale
  )
}

export function paginateVirtualGroups(
  groups: VirtualGroup[],
  page: number,
  size: number
): VirtualGroup[] {
  const p = Math.max(1, page)
  const s = Math.max(1, size)
  const start = (p - 1) * s
  return groups.slice(start, start + s)
}
