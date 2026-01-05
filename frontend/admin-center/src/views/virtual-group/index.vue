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

const { t } = useI18n()

const loading = ref(false)
const groups = ref<any[]>([])
const formDialogVisible = ref(false)
const membersDialogVisible = ref(false)
const currentGroup = ref<any>(null)

const typeText = (type: string) => ({ PROJECT: '项目组', TEMPORARY: '临时组', CROSS_DEPT: '跨部门组' }[type] || type)

const fetchGroups = async () => {
  loading.value = true
  // Mock data
  groups.value = [
    { id: '1', name: '项目A组', code: 'PROJECT_A', type: 'PROJECT', memberCount: 8, validFrom: '2026-01-01', validTo: '2026-12-31', status: 'ACTIVE' },
    { id: '2', name: '临时审批组', code: 'TEMP_APPROVAL', type: 'TEMPORARY', memberCount: 3, validFrom: '2026-01-01', validTo: '2026-03-31', status: 'ACTIVE' }
  ]
  loading.value = false
}

const showCreateDialog = () => { currentGroup.value = null; formDialogVisible.value = true }
const showEditDialog = (group: any) => { currentGroup.value = group; formDialogVisible.value = true }
const showMembersDialog = (group: any) => { currentGroup.value = group; membersDialogVisible.value = true }

const handleDelete = async (group: any) => {
  await ElMessageBox.confirm('确定要删除该虚拟组吗？', '提示', { type: 'warning' })
  groups.value = groups.value.filter(g => g.id !== group.id)
  ElMessage.success('删除成功')
}

onMounted(fetchGroups)
</script>
