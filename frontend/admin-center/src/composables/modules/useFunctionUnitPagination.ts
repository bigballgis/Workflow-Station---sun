/**
 * Function Unit list / archive / deployments pagination.
 *
 * List & Archive: client-side slice over deduplicated + keyword-filtered data.
 * Deployments: server-side page/size (caller fetches).
 */
import { reactive, computed, watch, type Ref } from 'vue'
import type { FunctionUnit } from '@/api/functionUnit'

export interface PaginationState {
  page: number
  size: number
}

function clampPage(pagination: PaginationState, total: number): void {
  const maxPage = Math.max(1, Math.ceil(total / pagination.size) || 1)
  if (pagination.page > maxPage) {
    pagination.page = maxPage
  }
}

export function useFunctionUnitPagination(options: {
  functionUnits: Ref<FunctionUnit[]>
  archivedFunctionUnits: Ref<FunctionUnit[]>
  searchKeyword: Ref<string>
  archiveSearchKeyword: Ref<string>
  /** Reload deployments from the server using current deploymentsPagination. */
  loadDeployments: () => void | Promise<void>
}) {
  const {
    functionUnits,
    archivedFunctionUnits,
    searchKeyword,
    archiveSearchKeyword,
    loadDeployments,
  } = options

  const listPagination = reactive<PaginationState>({ page: 1, size: 20 })
  const archivePagination = reactive<PaginationState>({ page: 1, size: 20 })
  const deploymentsPagination = reactive<PaginationState>({ page: 1, size: 20 })

  const filteredFunctionUnits = computed(() => {
    const keyword = searchKeyword.value.trim().toLowerCase()
    if (!keyword) return functionUnits.value
    return functionUnits.value.filter(unit =>
      (unit.name?.toLowerCase().includes(keyword)) ||
      (unit.code?.toLowerCase().includes(keyword)) ||
      (unit.description?.toLowerCase().includes(keyword))
    )
  })

  const filteredArchivedFunctionUnits = computed(() => {
    const keyword = archiveSearchKeyword.value.trim().toLowerCase()
    if (!keyword) return archivedFunctionUnits.value
    return archivedFunctionUnits.value.filter(unit =>
      (unit.name?.toLowerCase().includes(keyword)) ||
      (unit.code?.toLowerCase().includes(keyword)) ||
      (unit.description?.toLowerCase().includes(keyword))
    )
  })

  const listTotal = computed(() => filteredFunctionUnits.value.length)
  const archiveTotal = computed(() => filteredArchivedFunctionUnits.value.length)

  const pagedFunctionUnits = computed(() => {
    const start = (listPagination.page - 1) * listPagination.size
    return filteredFunctionUnits.value.slice(start, start + listPagination.size)
  })

  const pagedArchivedFunctionUnits = computed(() => {
    const start = (archivePagination.page - 1) * archivePagination.size
    return filteredArchivedFunctionUnits.value.slice(start, start + archivePagination.size)
  })

  // Keyword change → first page
  watch(searchKeyword, () => { listPagination.page = 1 })
  watch(archiveSearchKeyword, () => { archivePagination.page = 1 })

  // After delete/archive/filter shrink: avoid landing on an empty page
  watch(listTotal, (total) => { clampPage(listPagination, total) })
  watch(archiveTotal, (total) => { clampPage(archivePagination, total) })

  const handleListSizeChange = () => { listPagination.page = 1 }
  const handleArchiveSizeChange = () => { archivePagination.page = 1 }

  /** Single handler via el-pagination @change — avoids size+current double fetch. */
  const handleDeploymentsChange = () => {
    void loadDeployments()
  }

  return {
    listPagination,
    archivePagination,
    deploymentsPagination,
    filteredFunctionUnits,
    filteredArchivedFunctionUnits,
    listTotal,
    archiveTotal,
    pagedFunctionUnits,
    pagedArchivedFunctionUnits,
    handleListSizeChange,
    handleArchiveSizeChange,
    handleDeploymentsChange,
  }
}
