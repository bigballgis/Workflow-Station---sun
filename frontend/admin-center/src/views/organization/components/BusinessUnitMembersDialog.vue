<template>
  <el-dialog
    v-model="visible"
    :title="t('organization.members') + ' - ' + (businessUnit?.name || '')"
    width="700px"
    @open="handleDialogOpen"
  >
    <div class="members-header">
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
      <el-button type="primary" @click="handleAddMember" :disabled="!selectedUserId">
        {{ t('role.addMember') }}
      </el-button>
    </div>

    <el-table :data="members" v-loading="loading" stripe style="margin-top: 16px" max-height="400">
      <el-table-column prop="username" :label="t('user.username')" width="120" />
      <el-table-column prop="fullName" :label="t('user.fullName')" width="120" />
      <el-table-column prop="email" :label="t('user.email')" />
      <el-table-column :label="t('common.operation')" width="100">
        <template #default="{ row }">
          <el-button link type="danger" @click="handleRemoveMember(row)">
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && members.length === 0" :description="t('common.noData')" />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { businessUnitApi, type BusinessUnit } from '@/api/businessUnit'
import { userApi, type User } from '@/api/user'

const props = defineProps<{ businessUnit: BusinessUnit | null }>()
const visible = defineModel<boolean>({ default: false })
const emit = defineEmits(['success'])
const { t } = useI18n()

const loading = ref(false)
const searchLoading = ref(false)
const members = ref<any[]>([])
const searchResults = ref<User[]>([])
const selectedUserId = ref('')
const defaultUsersLoaded = ref(false)

const handleDialogOpen = () => {
  fetchMembers()
  defaultUsersLoaded.value = false
  searchResults.value = []
  selectedUserId.value = ''
}

const fetchMembers = async () => {
  if (!props.businessUnit) return
  loading.value = true
  try {
    const result = await businessUnitApi.getMembers(props.businessUnit.id, { page: 0, size: 100 })
    members.value = result.content || []
  } catch (e) {
    console.error('Failed to fetch members:', e)
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
    const memberIds = new Set(members.value.map(m => m.id))
    searchResults.value = result.content.filter(u => !memberIds.has(u.id))
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
    const memberIds = new Set(members.value.map(m => m.id))
    searchResults.value = result.content.filter(u => !memberIds.has(u.id))
  } catch (e) {
    console.error('Failed to search users:', e)
  } finally {
    searchLoading.value = false
  }
}

const handleAddMember = async () => {
  if (!props.businessUnit || !selectedUserId.value) return
  try {
    await businessUnitApi.addMember(props.businessUnit.id, selectedUserId.value)
    ElMessage.success(t('common.success'))
    selectedUserId.value = ''
    searchResults.value = []
    defaultUsersLoaded.value = false
    fetchMembers()
    emit('success')
  } catch (e: any) {
    console.error('Failed to add member:', e)
    ElMessage.error(e.response?.data?.message || t('common.failed'))
  }
}

const handleRemoveMember = async (member: any) => {
  await ElMessageBox.confirm(t('common.confirm'), t('common.confirm'), { type: 'warning' })
  try {
    await businessUnitApi.removeMember(props.businessUnit!.id, member.id)
    ElMessage.success(t('common.success'))
    fetchMembers()
    emit('success')
  } catch (e: any) {
    console.error('Failed to remove member:', e)
    ElMessage.error(e.response?.data?.message || t('common.failed'))
  }
}

watch(() => props.businessUnit, () => { if (visible.value) fetchMembers() })
</script>

<style scoped>
.members-header {
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>
