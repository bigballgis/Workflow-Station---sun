import { onBeforeUnmount, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { userApi, type User } from '@/api/user'
import { notifyError } from '@/utils/notify'

export interface TargetOption { id: string; label: string }

/** Pages the complete existing user scope; query generations also cover in-flight pagination. */
export function useBiTargetUsers(options: Ref<TargetOption[]>, selectedId: Ref<string>) {
  const { t } = useI18n()
  const loading = ref(false)
  const hasMore = ref(false)
  const failed = ref(false)
  const keyword = ref('')
  let page = 0
  let generation = 0
  let failedAppend = false
  let timer: ReturnType<typeof setTimeout> | undefined

  const label = (u: User): string => `${u.fullName || u.username} (${u.username}) · ${u.status}`

  function cancel(): void {
    generation++
    clearTimeout(timer)
    loading.value = false
  }

  async function load(append: boolean): Promise<void> {
    if (append && loading.value) return
    const current = generation
    const nextPage = append ? page + 1 : 0
    loading.value = true
    failed.value = false
    try {
      const params = keyword.value
        ? { keyword: keyword.value, page: nextPage, size: 20 }
        : { page: nextPage, size: 20 }
      const result = await userApi.list(params)
      if (current !== generation) return
      const selected = options.value.filter(o => o.id === selectedId.value)
      const base = append ? options.value : selected
      options.value = [...new Map([...base, ...result.content.map(u => ({ id: u.id, label: label(u) }))]
        .map(o => [o.id, o])).values()]
      page = nextPage
      hasMore.value = nextPage + 1 < result.totalPages
    } catch {
      if (current !== generation) return
      failedAppend = append
      failed.value = true
      notifyError(t('bi.assignment.loadTargetsFailed'))
    } finally {
      if (current === generation) loading.value = false
    }
  }

  async function searchUsers(query: string): Promise<void> {
    cancel()
    keyword.value = query.trim()
    hasMore.value = false
    options.value = options.value.filter(o => o.id === selectedId.value)
    await load(false)
  }

  function scheduleSearch(query: string): void {
    cancel()
    hasMore.value = false
    loading.value = true
    timer = setTimeout(() => { void searchUsers(query) }, 300)
  }

  const loadMore = (): Promise<void> => load(true)
  const retry = (): Promise<void> => load(failedAppend)
  onBeforeUnmount(cancel)
  return { loading, hasMore, failed, searchUsers, scheduleSearch, loadMore, retry, cancel }
}
