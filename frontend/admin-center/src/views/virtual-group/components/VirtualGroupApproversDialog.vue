<template>
  <el-dialog
    v-model="visible"
    :title="t('virtualGroup.approvers') + ' - ' + (group?.name || '')"
    width="600px"
    @open="handleDialogOpen"
  >
    <div class="approvers-header">
      <el-select
        v-model="selectedUserId"
        :placeholder="t('user.searchUserPlaceholder')"
        filterable
        remote
        :remote-method="searchUsers"
        :loading="searchLoading"
        style="width: 300px"
        @focus="loadDefaultUsers"
      >
        <el-option
          v-for="user in searchResults"
          :key="user.id"
          :label="`${user.fullName} (${user.username})`"
          :value="user.id"
        />
      </el-select>
      <el-button type="primary" @click="handleAddApprover" :disabled="!selectedUserId">
        {{ t('virtualGroup.addApprover') }}
      </el-button>
    </div>

    <el-table :data="approvers" v-loading="loading" stripe style="margin-top: 16px">
      <el-table-column prop="userFullName" :label="t('user.fullName')" />
      <el-table-column prop="userName" :label="t('user.username')" />
      <el-table-column :label="t('common.operation')" width="100">
        <template #default="{ row }">
          <el-button link type="danger" @click="handleRemoveApprover(row)">
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && approvers.length === 0" :description="t('virtualGroup.noApprovers')" />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { virtualGroupApi, type VirtualGroup, type Approver } from '@/api/virtualGroup'
import { userApi, type User } from '@/api/user'

const props = defineProps<{ group: VirtualGroup | null }>()
const visible = defineModel<boolean>({ default: false })
const { t } = useI18n()

const loading = ref(false)
const searchLoading = ref(false)
const approvers = ref<Approver[]>([])
const searchResults = ref<User[]>([])
const selectedUserId = ref('')
const defaultUsersLoaded = ref(false)

const handleDialogOpen = () => {
  fetchApprovers()
  defaultUsersLoaded.value = false
}

const fetchApprovers = async () => {
  if (!props.group) return
  loading.value = true
  try {
    approvers.value = await virtualGroupApi.getApprovers(props.group.id)
  } catch (e) {
    console.error('Failed to fetch approvers:', e)
    ElMessage.error(t('common.failed'))
  } finally {
    loading.value = false
  }
}

const loadDefaultUsers = async () => {
  if (defaultUsersLoaded.value || searchResults.value.length > 0) return
  searchLoading.value = true
  try {
    const result = await userApi.list({ size: 20 })
    const approverIds = new Set(approvers.value.map(a => a.userId))
    searchResults.value = result.content.filter(u => !approverIds.has(u.id))
    defaultUsersLoaded.value = true
  } catch (e) {
    console.error('Failed to load default users:', e)
  } finally {
    searchLoading.value = false
  }
}

const searchUsers = async (query: string) => {
  if (!query) {
    loadDefaultUsers()
    return
  }
  searchLoading.value = true
  try {
    const result = await userApi.list({ keyword: query, size: 20 })
    const approverIds = new Set(approvers.value.map(a => a.userId))
    searchResults.value = result.content.filter(u => !approverIds.has(u.id))
  } catch (e) {
    console.error('Failed to search users:', e)
  } finally {
    searchLoading.value = false
  }
}

const handleAddApprover = async () => {
  if (!props.group || !selectedUserId.value) return
  try {
    await virtualGroupApi.addApprover(props.group.id, selectedUserId.value)
    ElMessage.success(t('common.success'))
    selectedUserId.value = ''
    searchResults.value = []
    defaultUsersLoaded.value = false
    fetchApprovers()
  } catch (e) {
    console.error('Failed to add approver:', e)
    ElMessage.error(t('common.failed'))
  }
}

const handleRemoveApprover = async (approver: Approver) => {
  await ElMessageBox.confirm(t('common.confirm'), t('common.confirm'), { type: 'warning' })
  try {
    await virtualGroupApi.removeApprover(approver.id)
    ElMessage.success(t('common.success'))
    fetchApprovers()
  } catch (e) {
    console.error('Failed to remove approver:', e)
    ElMessage.error(t('common.failed'))
  }
}

watch(() => props.group, () => { if (visible.value) fetchApprovers() })
</script>

<style scoped>
.approvers-header {
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>
