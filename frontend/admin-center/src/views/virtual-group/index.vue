<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.virtualGroup') }}</span>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>{{ t('virtualGroup.create') }}
      </el-button>
    </div>
    
    <el-table :data="groups" v-loading="loading" stripe>
      <el-table-column prop="name" :label="t('virtualGroup.name')" />
      <el-table-column prop="code" :label="t('virtualGroup.code')" />
      <el-table-column prop="type" :label="t('virtualGroup.type')">
        <template #default="{ row }">
          <el-tag>{{ typeText(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="memberCount" :label="t('virtualGroup.memberCount')" width="100" />
      <el-table-column prop="validFrom" :label="t('virtualGroup.validityPeriod')">
        <template #default="{ row }">
          {{ row.validFrom }} ~ {{ row.validTo || t('common.permanent') }}
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="t('virtualGroup.status')">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? t('virtualGroup.active') : t('virtualGroup.inactive') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.operation')" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="showEditDialog(row)">{{ t('virtualGroup.edit') }}</el-button>
          <el-button link type="primary" @click="showMembersDialog(row)">{{ t('virtualGroup.members') }}</el-button>
          <el-button link type="danger" @click="handleDelete(row)">{{ t('virtualGroup.delete') }}</el-button>
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

const typeText = (type: string) => ({ PROJECT: t('role.functionRole'), TEMPORARY: t('role.tempRole'), WORK: t('role.businessRole'), TASK_HANDLER: t('role.businessRole') }[type] || type)

const fetchGroups = async () => {
  loading.value = true
  try {
    groups.value = await virtualGroupApi.list()
  } catch (e) {
    console.error('Failed to load virtual groups:', e)
    ElMessage.error(t('common.failed'))
  } finally {
    loading.value = false
  }
}

const showCreateDialog = () => { currentGroup.value = null; formDialogVisible.value = true }
const showEditDialog = (group: VirtualGroup) => { currentGroup.value = group; formDialogVisible.value = true }
const showMembersDialog = (group: VirtualGroup) => { currentGroup.value = group; membersDialogVisible.value = true }

const handleDelete = async (group: VirtualGroup) => {
  await ElMessageBox.confirm(t('common.confirm'), t('common.confirm'), { type: 'warning' })
  try {
    await virtualGroupApi.delete(group.id)
    ElMessage.success(t('common.success'))
    fetchGroups()
  } catch (e) {
    console.error('Failed to delete group:', e)
    ElMessage.error(t('common.failed'))
  }
}

onMounted(fetchGroups)
</script>
