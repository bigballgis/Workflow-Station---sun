<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.dictionary') }}</span>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>{{ t('dictionary.createDictionary') }}
      </el-button>
    </div>
    
    <el-row :gutter="20">
      <el-col :span="10">
        <el-card>
          <template #header>{{ t('dictionary.title') }}</template>
          <el-input v-model="filterText" :placeholder="t('dictionary.searchDictionary')" clearable style="margin-bottom: 15px" />
          <el-table :data="filteredDictionaries" highlight-current-row @current-change="handleDictSelect" max-height="500" v-loading="loading">
            <el-table-column prop="name" :label="t('dictionary.dictName')" />
            <el-table-column prop="code" :label="t('dictionary.dictCode')" width="120" />
            <el-table-column prop="type" :label="t('dictionary.dictType')" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ typeText(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('dictionary.dictActions')" width="100">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click.stop="showEditDialog(row)">{{ t('dictionary.dictEdit') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      
      <el-col :span="14">
        <el-card v-if="selectedDict">
          <template #header>
            <div class="dict-header">
              <span>{{ selectedDict.name }} - {{ t('dictionary.dictItems') }}</span>
              <el-button type="primary" size="small" @click="showItemDialog()">{{ t('dictionary.addItem') }}</el-button>
            </div>
          </template>
          
          <el-table :data="dictItems" row-key="id" default-expand-all :tree-props="{ children: 'children' }" v-loading="itemsLoading">
            <el-table-column prop="label" :label="t('dictionary.displayName')" />
            <el-table-column prop="value" :label="t('dictionary.dictValue')" width="120" />
            <el-table-column prop="sortOrder" :label="t('dictionary.dictSort')" width="80" />
            <el-table-column prop="status" :label="t('dictionary.dictStatus')" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status === 'ACTIVE' ? t('dictionary.statusActive') : t('dictionary.statusInactive') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('dictionary.dictActions')" width="150">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="showItemDialog(row)">{{ t('dictionary.dictEdit') }}</el-button>
                <el-button link type="primary" size="small" @click="showItemDialog(null, row)">{{ t('dictionary.addChild') }}</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteItem(row)">{{ t('dictionary.dictDelete') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-empty v-else :description="t('dictionary.selectDictHint')" />
      </el-col>
    </el-row>
    
    <DictionaryFormDialog v-model="formDialogVisible" :dictionary="currentDict" @success="fetchDictionaries" />
    <DictionaryItemDialog v-model="itemDialogVisible" :item="currentItem" :parent="parentItem" :dictionary-id="selectedDict?.id" @success="fetchDictItems" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import DictionaryFormDialog from './components/DictionaryFormDialog.vue'
import DictionaryItemDialog from './components/DictionaryItemDialog.vue'
import { dictionaryApi, type Dictionary, type DictionaryItem } from '@/api/dictionary'

const { t } = useI18n()

const filterText = ref('')
const loading = ref(false)
const itemsLoading = ref(false)
const dictionaries = ref<Dictionary[]>([])
const selectedDict = ref<Dictionary | null>(null)
const dictItems = ref<DictionaryItem[]>([])
const formDialogVisible = ref(false)
const itemDialogVisible = ref(false)
const currentDict = ref<Dictionary | null>(null)
const currentItem = ref<DictionaryItem | null>(null)
const parentItem = ref<DictionaryItem | null>(null)

const filteredDictionaries = computed(() => (dictionaries.value || []).filter(d => !filterText.value || d.name.includes(filterText.value) || d.code.includes(filterText.value)))
const typeText = (type: string) => ({ SYSTEM: t('dictionary.typeSystem'), BUSINESS: t('dictionary.typeBusiness'), CUSTOM: t('dictionary.typeCustom') }[type] || type)

const fetchDictionaries = async () => {
  loading.value = true
  try {
    const result = await dictionaryApi.list()
    dictionaries.value = Array.isArray(result) ? result : []
  } catch (e) {
    console.error('Failed to load dictionaries:', e)
    dictionaries.value = []
    ElMessage.error(t('dictionary.loadListFailed'))
  } finally {
    loading.value = false
  }
}

const fetchDictItems = async () => {
  if (!selectedDict.value) return
  itemsLoading.value = true
  try {
    dictItems.value = await dictionaryApi.getItems(selectedDict.value.id)
  } catch (e) {
    console.error('Failed to load dictionary items:', e)
    ElMessage.error(t('dictionary.loadItemsFailed'))
  } finally {
    itemsLoading.value = false
  }
}

const handleDictSelect = (dict: Dictionary | null) => {
  selectedDict.value = dict
  if (dict) fetchDictItems()
}

const showCreateDialog = () => { currentDict.value = null; formDialogVisible.value = true }
const showEditDialog = (dict: Dictionary) => { currentDict.value = dict; formDialogVisible.value = true }
const showItemDialog = (item?: DictionaryItem, parent?: DictionaryItem) => {
  currentItem.value = item || null
  parentItem.value = parent || null
  itemDialogVisible.value = true
}

const handleDeleteItem = async (item: DictionaryItem) => {
  await ElMessageBox.confirm(t('dictionary.deleteConfirm'), t('dictionary.deleteConfirmTitle'), { type: 'warning' })
  try {
    await dictionaryApi.deleteItem(item.id)
    ElMessage.success(t('dictionary.deleteSuccess'))
    fetchDictItems()
  } catch (e) {
    console.error('Failed to delete item:', e)
    ElMessage.error(t('dictionary.deleteFailed'))
  }
}

onMounted(fetchDictionaries)
</script>

<style scoped>
.dict-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
