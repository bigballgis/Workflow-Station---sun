/**
 * 通用分页 + 搜索 + 重置 composable
 * 
 * 消除所有列表页中重复的 handleSearch / handleReset / query reactive 模式。
 * 
 * @example
 * const { data, loading, query, handleSearch, handleReset } = usePagination(
 *   (params) => userApi.list(params),
 *   { keyword: '', status: '' }
 * )
 */

import { ref, reactive, type Ref } from 'vue'

export interface PaginationQuery {
  page: number
  size: number
  [key: string]: unknown
}

export interface PaginationResult<T> {
  content: T[]
  totalElements: number
}

export interface UsePaginationOptions<TQuery extends PaginationQuery, TItem> {
  /** API 请求函数，接收 query 参数，返回分页结果 */
  fetchFn: (query: TQuery) => Promise<PaginationResult<TItem>>
  /** 初始查询参数（不含 page/size，会自动补充） */
  initialQuery?: Partial<TQuery>
  /** 默认每页数量 */
  defaultPageSize?: number
  /** 错误提示消息 */
  errorMessage?: string
}

export function usePagination<TQuery extends PaginationQuery, TItem>(
  fetchFn: (query: TQuery) => Promise<PaginationResult<TItem>>,
  initialQuery: Partial<TQuery> = {},
  options?: Omit<UsePaginationOptions<TQuery, TItem>, 'fetchFn' | 'initialQuery'>
) {
  const defaultPageSize = options?.defaultPageSize ?? 20
  const errorMessage = options?.errorMessage ?? 'Query failed'

  const data = ref<TItem[]>([]) as Ref<TItem[]>
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const defaultQuery = {
    page: 1,
    size: defaultPageSize,
    ...initialQuery,
  } as TQuery

  const query = reactive<TQuery>({ ...defaultQuery })

  /**
   * 执行查询。模板中直接绑定 @current-change / @size-change / @click
   */
  const handleSearch = async () => {
    loading.value = true
    try {
      // 过滤空字符串（后端枚举不接受空值），保持与原始 query.xxx || undefined 一致
      const cleanQuery = Object.fromEntries(
        Object.entries(query).filter(([_, v]) => v !== '' && v !== undefined && v !== null)
      )
      const params = {
        ...cleanQuery,
        page: query.page - 1, // 后端 0-based
      } as TQuery
      const result = await fetchFn(params)
      data.value = result.content
      total.value = result.totalElements
    } catch (err: unknown) {
      const e = err as { message?: string; code?: string }
      error.value = e.message || e.code || errorMessage
    } finally {
      loading.value = false
    }
  }

  /**
   * 重置查询参数并重新搜索
   */
  const handleReset = () => {
    Object.assign(query, { ...defaultQuery })
    handleSearch()
  }

  return {
    data,
    total,
    loading,
    error,
    query,
    handleSearch,
    handleReset,
  }
}
