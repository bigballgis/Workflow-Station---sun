/**
 * Tag storage utility for function units
 * Stores tags in localStorage until backend support is added
 */

import i18n from '@/i18n'

const TAGS_STORAGE_KEY = 'function-unit-tags'

/**
 * Get predefined tags with i18n translations
 */
export const getPredefinedTags = (): string[] => {
  const { t } = i18n.global
  return [
    t('tags.coreBusiness'),
    t('tags.reports'),
    t('tags.approvalProcess'),
    t('tags.dataManagement'),
    t('tags.systemIntegration'),
    t('tags.userManagement'),
  ]
}

/**
 * @deprecated Use getPredefinedTags() instead for dynamic i18n support
 */
export const PREDEFINED_TAGS = getPredefinedTags()

interface TagData {
  [functionUnitId: number]: string[]
}

/**
 * Get all tags data from localStorage
 */
export function getAllTagsData(): TagData {
  try {
    const stored = localStorage.getItem(TAGS_STORAGE_KEY)
    return stored ? JSON.parse(stored) : {}
  } catch (e) {
    console.error('Failed to parse tags data:', e)
    return {}
  }
}

/**
 * Get tags for a specific function unit
 */
export function getTags(functionUnitId: number): string[] {
  const data = getAllTagsData()
  return data[functionUnitId] || []
}

/**
 * Set tags for a specific function unit
 */
export function setTags(functionUnitId: number, tags: string[]): void {
  try {
    const data = getAllTagsData()
    data[functionUnitId] = tags
    localStorage.setItem(TAGS_STORAGE_KEY, JSON.stringify(data))
  } catch (e) {
    console.error('Failed to save tags:', e)
  }
}

/**
 * Remove tags for a specific function unit
 */
export function removeTags(functionUnitId: number): void {
  try {
    const data = getAllTagsData()
    delete data[functionUnitId]
    localStorage.setItem(TAGS_STORAGE_KEY, JSON.stringify(data))
  } catch (e) {
    console.error('Failed to remove tags:', e)
  }
}

/**
 * Get all unique tags used across all function units
 */
export function getAllUsedTags(): string[] {
  const data = getAllTagsData()
  const allTags = new Set<string>()
  
  Object.values(data).forEach(tags => {
    tags.forEach(tag => allTags.add(tag))
  })
  
  return Array.from(allTags)
}

/**
 * Get all available tags (predefined + custom used tags)
 */
export function getAllAvailableTags(): string[] {
  const usedTags = getAllUsedTags()
  const allTags = new Set([...getPredefinedTags(), ...usedTags])
  return Array.from(allTags).sort()
}

/**
 * Filter function for tag display logic
 * Returns the tags to display and the count of extra tags
 */
export function getDisplayTags(tags: string[], maxDisplay: number = 3): { 
  displayTags: string[]
  extraCount: number 
} {
  return {
    displayTags: tags.slice(0, maxDisplay),
    extraCount: Math.max(0, tags.length - maxDisplay)
  }
}

/**
 * Filter function units by tags
 * Returns true if the function unit has ALL the specified tags
 */
export function matchesTags(functionUnitTags: string[], filterTags: string[]): boolean {
  if (filterTags.length === 0) return true
  return filterTags.every(tag => functionUnitTags.includes(tag))
}
