import { reactive, computed } from 'vue'
import type { Ref } from 'vue'
import type { FunctionUnitResponse } from '@/api/functionUnit'
import { normalizeTags, collectAvailableTags } from '@/utils/tagStorage'

interface UseFunctionUnitFiltersOptions {
  list: Ref<FunctionUnitResponse[]>
  allTags: Ref<string[]>
  resetPage: () => void
  reload: () => void
}

/** Search form state and client-side filtering (name/status only; tags are server-filtered). */
export function useFunctionUnitFilters(options: UseFunctionUnitFiltersOptions) {
  const { list, allTags, resetPage, reload } = options

  // Default: show all statuses so rollback/import drafts are not hidden from the home list.
  const searchForm = reactive({ name: '', status: '', tags: [] as string[] })

  // 下拉选项来自服务端返回的全部 tag（跨所有功能单元），确保新建 tag 首次出现即可选
  const availableTags = computed(() => {
    // Merge: server-side allTags + any tags from current page (in case server hasn't picked up latest)
    const fromList = collectAvailableTags(list.value)
    const merged = new Set([...allTags.value, ...fromList])
    return Array.from(merged).sort()
  })

  function getItemTags(item: FunctionUnitResponse): string[] {
    return normalizeTags(item.tags)
  }

  // Client-side filtering: name and status only (tags are already filtered server-side via API param).
  // Results are also sorted alphabetically by name (first letter, locale-aware).
  const filteredList = computed(() => {
    const filtered = list.value.filter(item => {
      if (searchForm.name && !item.name.toLowerCase().includes(searchForm.name.toLowerCase())) {
        return false
      }
      if (searchForm.status && item.status !== searchForm.status) {
        return false
      }
      return true
    })
    // Sort alphabetically by name, case-insensitive
    return [...filtered].sort((a, b) =>
      a.name.localeCompare(b.name, undefined, { sensitivity: 'base', numeric: true })
    )
  })

  function handleSearch() {
    resetPage()
    reload()
  }

  function clearFilters() {
    searchForm.name = ''
    searchForm.status = ''
    searchForm.tags = []
    handleSearch()
  }

  const hiddenByStatusCount = computed(() => {
    if (!searchForm.status) {
      return 0
    }
    return list.value.filter(item => item.status !== searchForm.status).length
  })

  return {
    searchForm,
    availableTags,
    filteredList,
    hiddenByStatusCount,
    getItemTags,
    handleSearch,
    clearFilters,
  }
}
