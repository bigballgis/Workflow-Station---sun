/**
 * Function Unit 标签工具（纯函数，无 localStorage）。
 * 标签持久化在 dw_function_units.tags（JSONB），由后端 API 读写。
 */

const MAX_TAGS = 20
const MAX_TAG_LENGTH = 50

/** 规范化标签：trim、去空、去重、长度与数量上限。 */
export function normalizeTags(tags: string[] | undefined | null): string[] {
  if (!tags?.length) return []
  const unique = new Set<string>()
  for (const raw of tags) {
    const trimmed = raw?.trim()
    if (!trimmed || trimmed.length > MAX_TAG_LENGTH) continue
    unique.add(trimmed)
    if (unique.size >= MAX_TAGS) break
  }
  return Array.from(unique)
}

/** 从功能单元列表收集所有已用标签（供 Filter 下拉，随 list 响应式更新）。 */
export function collectAvailableTags(items: Array<{ tags?: string[] }>): string[] {
  const all = new Set<string>()
  for (const item of items) {
    normalizeTags(item.tags).forEach(tag => all.add(tag))
  }
  return Array.from(all).sort()
}

/** 卡片展示：最多 maxDisplay 个 + 余量计数。 */
export function getDisplayTags(tags: string[], maxDisplay: number = 3): {
  displayTags: string[]
  extraCount: number
} {
  const normalized = normalizeTags(tags)
  return {
    displayTags: normalized.slice(0, maxDisplay),
    extraCount: Math.max(0, normalized.length - maxDisplay),
  }
}

/** 筛选：item 须包含 filter 中的全部 tag（AND 语义）。 */
export function matchesTags(functionUnitTags: string[], filterTags: string[]): boolean {
  if (filterTags.length === 0) return true
  const itemTags = normalizeTags(functionUnitTags)
  const normalizedFilterTags = normalizeTags(filterTags)
  if (normalizedFilterTags.length === 0) return true
  return normalizedFilterTags.every(tag => itemTags.includes(tag))
}
