/**
 * 版本号 & 数据去重工具函数
 */

/**
 * 比较两个语义化版本号
 * @returns 正数表示 a > b，负数表示 a < b，0 表示相等
 */
export const compareVersions = (a: string, b: string): number => {
  const pa = (a || '0.0.0').split('.').map(Number)
  const pb = (b || '0.0.0').split('.').map(Number)
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const na = pa[i] || 0
    const nb = pb[i] || 0
    if (na !== nb) return na - nb
  }
  return 0
}

/**
 * 按 code 分组，每组只保留 version 最高的记录
 * 适用于功能单元等多版本实体列表的去重展示
 */
export const deduplicateByCode = <T extends { code: string; version: string }>(
  units: T[]
): T[] => {
  const map = new Map<string, T>()
  for (const unit of units) {
    const existing = map.get(unit.code)
    if (!existing || compareVersions(unit.version, existing.version) > 0) {
      map.set(unit.code, unit)
    }
  }
  return Array.from(map.values())
}

/**
 * 获取数组中最大的版本号
 */
export const maxVersion = (versions: string[]): string => {
  if (!versions.length) return '0.0.0'
  return versions.reduce((max, v) => (compareVersions(v, max) > 0 ? v : max))
}
