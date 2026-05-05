/**
 * 字典管理业务逻辑 composable
 *
 * 封装 dictionary/index.vue 页面的所有 API 调用和业务逻辑。
 * 组件仅保留 template + 调用此 composable。
 */

import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { ElMessage } from 'element-plus'
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
      console.error('Failed to load dictionaries:', e)
      ElMessage.error(t('dictionary.loadListFailed'))
    }
  }

  const fetchDictItems = async () => {
    if (!selectedDict.value) return
    itemsLoading.value = true
    try {
      dictItems.value = await store.fetchItems(selectedDict.value.id)
    } catch (e) {
      console.error('Failed to load dictionary items:', e)
      ElMessage.error(t('dictionary.loadItemsFailed'))
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
      successMessage: t('dictionary.deleteSuccess'),
      errorMessage: t('dictionary.deleteFailed'),
      onSuccess: fetchDictItems,
    }
  )

  const handleDeleteItem = (item: DictionaryItem) => deleteById(item.id)

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
