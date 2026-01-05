<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.dictionary') }}</span>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>创建字典
      </el-button>
    </div>
    
    <el-row :gutter="20">
      <el-col :span="10">
        <el-card>
          <template #header>字典列表</template>
          <el-input v-model="filterText" placeholder="搜索字典" clearable style="margin-bottom: 15px" />
          <el-table :data="filteredDictionaries" highlight-current-row @current-change="handleDictSelect" max-height="500">
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
          
          <el-table :data="dictItems" row-key="id" default-expand-all :tree-props="{ children: 'children' }">
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
                <el-button link type="primary" size="small" @click="showItemDialog(null, row)">添加子项</el-button>
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

const { t } = useI18n()

const filterText = ref('')
const dictionaries = ref<any[]>([])
const selectedDict = ref<any>(null)
const dictItems = ref<any[]>([])
const formDialogVisible = ref(false)
const itemDialogVisible = ref(false)
const currentDict = ref<any>(null)
const currentItem = ref<any>(null)
const parentItem = ref<any>(null)

const filteredDictionaries = computed(() => dictionaries.value.filter(d => !filterText.value || d.name.includes(filterText.value) || d.code.includes(filterText.value)))
const typeText = (type: string) => ({ SYSTEM: '系统', BUSINESS: '业务', CUSTOM: '自定义' }[type] || type)

const fetchDictionaries = () => {
  dictionaries.value = [
    { id: '1', name: '用户状态', code: 'USER_STATUS', type: 'SYSTEM' },
    { id: '2', name: '审批结果', code: 'APPROVAL_RESULT', type: 'BUSINESS' },
    { id: '3', name: '优先级', code: 'PRIORITY', type: 'CUSTOM' }
  ]
}

const fetchDictItems = () => {
  if (!selectedDict.value) return
  dictItems.value = [
    { id: '1', label: '启用', value: 'ENABLED', sortOrder: 1, status: 'ACTIVE' },
    { id: '2', label: '禁用', value: 'DISABLED', sortOrder: 2, status: 'ACTIVE' },
    { id: '3', label: '锁定', value: 'LOCKED', sortOrder: 3, status: 'ACTIVE' }
  ]
}

const handleDictSelect = (dict: any) => {
  selectedDict.value = dict
  fetchDictItems()
}

const showCreateDialog = () => { currentDict.value = null; formDialogVisible.value = true }
const showEditDialog = (dict: any) => { currentDict.value = dict; formDialogVisible.value = true }
const showItemDialog = (item?: any, parent?: any) => {
  currentItem.value = item || null
  parentItem.value = parent || null
  itemDialogVisible.value = true
}

const handleDeleteItem = async (item: any) => {
  await ElMessageBox.confirm('确定要删除该字典项吗？', '提示', { type: 'warning' })
  dictItems.value = dictItems.value.filter(i => i.id !== item.id)
  ElMessage.success('删除成功')
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
