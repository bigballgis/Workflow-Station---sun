/**
 * Module-level FU content cache: same processDefinitionKey → same FU content.
 * Survives component remount when navigating between To Do detail pages of the same process.
 * TTL 5 min; max 10 entries (LRU via Map insertion order).
 *
 * NOTE: declared inside `<script setup>` in the original detail.vue, so the cache
 * lifetime is per component instance — `createFuContentCache()` preserves that.
 */
export function createFuContentCache() {
  const __fuContentCache = new Map<string, { payload: any; ts: number }>()
  const FU_CACHE_TTL = 5 * 60 * 1000
  const FU_CACHE_MAX = 10
  function getCachedFuContent(key: string): any | null {
    const entry = __fuContentCache.get(key)
    if (!entry) return null
    if (Date.now() - entry.ts > FU_CACHE_TTL) {
      __fuContentCache.delete(key)
      return null
    }
    return entry.payload
  }
  function setCachedFuContent(key: string, payload: any) {
    if (__fuContentCache.size >= FU_CACHE_MAX) {
      // Delete oldest entry (first key in insertion order)
      const oldest = __fuContentCache.keys().next().value
      if (oldest) __fuContentCache.delete(oldest)
    }
    __fuContentCache.set(key, { payload, ts: Date.now() })
  }
  return { getCachedFuContent, setCachedFuContent }
}
