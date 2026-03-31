/**
 * Bilingual status matching utilities.
 * Backend may return node names or error messages in either Chinese or English.
 * These helpers ensure consistent matching regardless of language.
 * TODO: Remove Chinese fallbacks once backend guarantees English-only identifiers.
 */

const REJECTED_KEYWORDS = ['rejected', '拒绝', '驳回']
const DISABLED_KEYWORDS = ['disabled', '禁用']

function matchesAny(value: string, keywords: string[]): boolean {
  const lower = value.toLowerCase()
  return keywords.some(k => lower.includes(k))
}

export function isRejectedName(name: string): boolean {
  return matchesAny(name, REJECTED_KEYWORDS)
}

export function isDisabledMessage(message: string): boolean {
  return matchesAny(message, DISABLED_KEYWORDS)
}
