/**
 * 字典管理业务逻辑 composable
 *
 * 封装 dictionary/index.vue 页面的所有 API 调用和业务逻辑。
 * 组件仅保留 template + 调用此 composable。
 */

import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { logger } from '@/utils/logger'
import { notifyError, notifySuccess } from '@/utils/notify'
import { storeToRefs } from 'pinia'
import { useDictionaryStore } from '@/stores/dictionary'
import type { Dictionary, DictionaryItem } from '@/api/dictionary'

export function useDictionary() {
  const { t } = useI18n()
  const store = useDictionaryStore()
  const { dictionaries, loading } = storeToRefs(store)

  // ==================== State ====================

  const filterText = ref('')
  const itemsLoading = ref(false)
  const selectedDict = ref<Dictionary | null>(null)
  const dictItems = ref<DictionaryItem[]>([])
  const formDialogVisible = ref(false)
  const itemDialogVisible = ref(false)
  const currentDict = ref<Dictionary | null>(null)
  const currentItem = ref<DictionaryItem | null>(null)
  const parentItem = ref<DictionaryItem | null>(null)

  // ==================== Computed ====================

  const filteredDictionaries = computed(() =>
    (dictionaries.value || []).filter(
      d =>
        !filterText.value ||
        d.name.includes(filterText.value) ||
        d.code.includes(filterText.value)
    )
  )

  // ==================== Data Fetching ====================

  const fetchDictionaries = async () => {
    try {
      await store.fetchDictionaries()
    } catch (e) {
      logger.error('dictionary', 'Failed to load dictionaries:', e)
      notifyError(t(errorTranslator(AppErrorCode.DICTIONARY_LOAD_LIST_FAILED)))
    }
  }

  const fetchDictItems = async () => {
    if (!selectedDict.value) return
    itemsLoading.value = true
    try {
      dictItems.value = await store.fetchItems(selectedDict.value.id)
    } catch (e) {
      logger.error('dictionary', 'Failed to load dictionary items:', e)
      notifyError(t(errorTranslator(AppErrorCode.DICTIONARY_LOAD_ITEMS_FAILED)))
    } finally {
      itemsLoading.value = false
    }
  }

  // ==================== Dialog Actions ====================

  const handleDictSelect = (dict: Dictionary | null) => {
    selectedDict.value = dict
    if (dict) fetchDictItems()
  }

  const showCreateDialog = () => {
    currentDict.value = null
    formDialogVisible.value = true
  }

  const showEditDialog = (dict: Dictionary) => {
    currentDict.value = dict
    formDialogVisible.value = true
  }

  const showItemDialog = (item?: DictionaryItem, parent?: DictionaryItem) => {
    currentItem.value = item || null
    parentItem.value = parent || null
    itemDialogVisible.value = true
  }

  // ==================== Delete ====================

  const { handleDelete: deleteById } = useConfirmDelete(
    (id: string) => store.deleteItem(id),
    {
      confirmMessage: t('dictionary.deleteConfirm'),
      confirmTitle: t('dictionary.deleteConfirmTitle'),
      onSuccess: fetchDictItems,
    }
  )

  const handleDeleteItem = async (item: DictionaryItem) => {
    const r = await deleteById(item.id)
    if (r.cancelled) return
    if (r.ok) notifySuccess(t('dictionary.deleteSuccess'))
    else notifyError(t(errorTranslator(r.code || AppErrorCode.DICTIONARY_LOAD_ITEMS_FAILED)))
  }

  // ==================== Return ====================

  return {
    // State
    filterText,
    loading,
    itemsLoading,
    dictionaries,
    selectedDict,
    dictItems,
    formDialogVisible,
    itemDialogVisible,
    currentDict,
    currentItem,
    parentItem,
    // Computed
    filteredDictionaries,
    // Methods
    fetchDictionaries,
    fetchDictItems,
    handleDictSelect,
    showCreateDialog,
    showEditDialog,
    showItemDialog,
    handleDeleteItem,
  }
}
