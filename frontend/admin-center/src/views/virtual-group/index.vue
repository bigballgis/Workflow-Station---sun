<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.virtualGroup') }}</span>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>创建虚拟组
      </el-button>
    </div>
    
    <el-table :data="groups" v-loading="loading" stripe>
      <el-table-column prop="name" label="虚拟组名称" />
      <el-table-column prop="code" label="编码" />
      <el-table-column prop="type" label="类型">
        <template #default="{ row }">
          <el-tag>{{ typeText(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="memberCount" label="成员数" width="100" />
      <el-table-column prop="validFrom" label="有效期">
        <template #default="{ row }">
          {{ row.validFrom }} ~ {{ row.validTo || '永久' }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="showEditDialog(row)">编辑</el-button>
          <el-button link type="primary" @click="showMembersDialog(row)">成员</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <VirtualGroupFormDialog v-model="formDialogVisible" :group="currentGroup" @success="fetchGroups" />
    <VirtualGroupMembersDialog v-model="membersDialogVisible" :group="currentGroup" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import VirtualGroupFormDialog from './components/VirtualGroupFormDialog.vue'
import VirtualGroupMembersDialog from './components/VirtualGroupMembersDialog.vue'
import { virtualGroupApi, type VirtualGroup } from '@/api/virtualGroup'

const { t } = useI18n()

const loading = ref(false)
const groups = ref<VirtualGroup[]>([])
const formDialogVisible = ref(false)
const membersDialogVisible = ref(false)
const currentGroup = ref<VirtualGroup | null>(null)

const typeText = (type: string) => ({ PROJECT: '项目组', TEMPORARY: '临时组', WORK: '工作组', TASK_HANDLER: '任务处理组' }[type] || type)

const fetchGroups = async () => {
  loading.value = true
  try {
    groups.value = await virtualGroupApi.list()
  } catch (e) {
    console.error('Failed to load virtual groups:', e)
    ElMessage.error('加载虚拟组列表失败')
  } finally {
    loading.value = false
  }
}

const showCreateDialog = () => { currentGroup.value = null; formDialogVisible.value = true }
const showEditDialog = (group: VirtualGroup) => { currentGroup.value = group; formDialogVisible.value = true }
const showMembersDialog = (group: VirtualGroup) => { currentGroup.value = group; membersDialogVisible.value = true }

const handleDelete = async (group: VirtualGroup) => {
  await ElMessageBox.confirm('确定要删除该虚拟组吗？', '提示', { type: 'warning' })
  try {
    await virtualGroupApi.delete(group.id)
    ElMessage.success('删除成功')
    fetchGroups()
  } catch (e) {
    console.error('Failed to delete group:', e)
    ElMessage.error('删除失败')
  }
}

onMounted(fetchGroups)
</script>
