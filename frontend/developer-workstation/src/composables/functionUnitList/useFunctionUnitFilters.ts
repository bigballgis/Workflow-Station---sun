import { reactive, computed } from 'vue'
import type { Ref } from 'vue'
import type { FunctionUnitResponse } from '@/api/functionUnit'
import { getTags, getAllAvailableTags, matchesTags } from '@/utils/tagStorage'

interface UseFunctionUnitFiltersOptions {
  list: Ref<FunctionUnitResponse[]>
  resetPage: () => void
}

/** Search form state and client-side filtering for the function unit list. */
export function useFunctionUnitFilters(options: UseFunctionUnitFiltersOptions) {
  const { list, resetPage } = options

  const searchForm = reactive({ name: '', status: '', tags: [] as string[] })

  // Get all available tags for filter dropdown
  const availableTags = computed(() => getAllAvailableTags())

  // Get tags for a specific item
  function getItemTags(id: number): string[] {
    return getTags(id)
  }

  // Filter list based on search criteria
  const filteredList = computed(() => {
    return list.value.filter(item => {
      // Filter by name
      if (searchForm.name && !item.name.toLowerCase().includes(searchForm.name.toLowerCase())) {
        return false
      }
      // Filter by status
      if (searchForm.status && item.status !== searchForm.status) {
        return false
      }
      // Filter by tags
      if (searchForm.tags.length > 0) {
        const itemTags = getTags(item.id)
        if (!matchesTags(itemTags, searchForm.tags)) {
          return false
        }
      }
      return true
    })
  })

  function handleSearch() {
    resetPage()
    // Client-side filtering, no need to reload
  }

  function clearFilters() {
    searchForm.name = ''
    searchForm.status = ''
    searchForm.tags = []
  }

  return {
    searchForm,
    availableTags,
    filteredList,
    getItemTags,
    handleSearch,
    clearFilters,
  }
}
