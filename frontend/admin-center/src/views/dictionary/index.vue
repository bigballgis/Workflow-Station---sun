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
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="code" label="编码" width="120" />
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ typeText(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click.stop="showEditDialog(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      
      <el-col :span="14">
        <el-card v-if="selectedDict">
          <template #header>
            <div class="dict-header">
              <span>{{ selectedDict.name }} - 字典项</span>
              <el-button type="primary" size="small" @click="showItemDialog()">添加字典项</el-button>
            </div>
          </template>
          
          <el-table :data="dictItems" row-key="id" default-expand-all :tree-props="{ children: 'children' }" v-loading="itemsLoading">
            <el-table-column prop="label" label="显示名称" />
            <el-table-column prop="value" label="值" width="120" />
            <el-table-column prop="sortOrder" label="排序" width="80" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status === 'ACTIVE' ? '启用' : '禁用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="showItemDialog(row)">编辑</el-button>
                <el-button link type="primary" size="small" @click="showItemDialog(undefined, row)">添加子项</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteItem(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-empty v-else description="请选择字典查看详情" />
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
const typeText = (type: string) => ({ SYSTEM: '系统', BUSINESS: '业务', CUSTOM: '自定义' }[type] || type)

const fetchDictionaries = async () => {
  loading.value = true
  try {
    const result = await dictionaryApi.list()
    dictionaries.value = Array.isArray(result) ? result : []
  } catch (e) {
    console.error('Failed to load dictionaries:', e)
    dictionaries.value = []
    ElMessage.error('加载字典列表失败')
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
    ElMessage.error('加载字典项失败')
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
  await ElMessageBox.confirm('确定要删除该字典项吗？', '提示', { type: 'warning' })
  try {
    await dictionaryApi.deleteItem(item.id)
    ElMessage.success('删除成功')
    fetchDictItems()
  } catch (e) {
    console.error('Failed to delete item:', e)
    ElMessage.error('删除失败')
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
