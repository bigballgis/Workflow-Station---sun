<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="`${t('virtualGroup.members')} - ${group?.name}`" width="1100px">
    <div class="members-header">
      <el-button type="primary" size="small" @click="openAddDialog">{{ t('role.addMember') }}</el-button>
    </div>
    
    <el-table :data="members" v-loading="loading" max-height="400">
      <el-table-column prop="employeeId" :label="t('user.employeeId')" width="100" />
      <el-table-column prop="fullName" :label="t('user.fullName')" width="120" />
      <el-table-column prop="username" :label="t('user.username')" width="120" />
      <el-table-column prop="email" :label="t('user.email')" width="200" />
      <el-table-column prop="role" :label="t('user.role')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.role === 'LEADER' ? 'warning' : 'info'" size="small">
            {{ row.role === 'LEADER' ? t('organization.leader') : t('role.members') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="joinedAt" :label="t('common.createTime')" width="170">
        <template #default="{ row }">
          {{ row.joinedAt ? new Date(row.joinedAt).toLocaleString('zh-CN') : '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="t('common.operation')" width="80">
        <template #default="{ row }">
          <el-button link type="danger" @click="handleRemove(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <el-dialog v-model="showAddDialog" :title="t('role.addMember')" width="400px" append-to-body>
      <el-form label-width="80px">
        <el-form-item :label="t('role.selectUser')">
          <el-select 
            v-model="newMember.userId" 
            filterable 
            remote
            reserve-keyword
            :placeholder="t('virtualGroup.searchUserPlaceholder')"
            :remote-method="searchUsers"
            :loading="searchLoading"
            style="width: 100%"
            @focus="loadDefaultUsers"
          >
            <el-option 
              v-for="user in userOptions" 
              :key="user.id" 
              :label="`${user.fullName} (${user.username})`" 
              :value="user.id" 
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('user.role')">
          <el-select v-model="newMember.role" style="width: 100%">
            <el-option :label="t('organization.leader')" value="LEADER" />
            <el-option :label="t('role.members')" value="MEMBER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="addLoading" @click="handleAdd">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { virtualGroupApi, type VirtualGroupMember } from '@/api/virtualGroup'
import { userApi, type User } from '@/api/user'

const { t } = useI18n()

const props = defineProps<{ modelValue: boolean; group: any }>()
const emit = defineEmits(['update:modelValue'])

const loading = ref(false)
const members = ref<VirtualGroupMember[]>([])
const showAddDialog = ref(false)
const addLoading = ref(false)
const searchLoading = ref(false)
const userOptions = ref<User[]>([])
const newMember = reactive({ userId: '', role: 'MEMBER' as 'LEADER' | 'MEMBER' })

watch(() => props.modelValue, async (val) => {
  if (val && props.group) {
    await loadMembers()
  }
})

const loadMembers = async () => {
  if (!props.group) return
  loading.value = true
  try {
    members.value = await virtualGroupApi.getMembers(props.group.id)
  } catch (error: any) {
    console.error('Failed to load members:', error)
    members.value = []
  } finally {
    loading.value = false
  }
}

const openAddDialog = () => {
  newMember.userId = ''
  newMember.role = 'MEMBER'
  userOptions.value = []
  showAddDialog.value = true
}

const loadDefaultUsers = async () => {
  if (userOptions.value.length > 0) return
  searchLoading.value = true
  try {
    const result = await userApi.list({ size: 20 })
    userOptions.value = result.content
  } catch (error) {
    console.error('Failed to load users:', error)
  } finally {
    searchLoading.value = false
  }
}

const searchUsers = async (query: string) => {
  if (!query) {
    await loadDefaultUsers()
    return
  }
  searchLoading.value = true
  try {
    const result = await userApi.list({ keyword: query, size: 20 })
    userOptions.value = result.content
  } catch (error) {
    console.error('Failed to search users:', error)
  } finally {
    searchLoading.value = false
  }
}

const handleRemove = async (member: VirtualGroupMember) => {
  try {
    await ElMessageBox.confirm(t('common.confirm'), t('common.confirm'), { type: 'warning' })
    await virtualGroupApi.removeMember(props.group.id, member.userId)
    await loadMembers()
    ElMessage.success(t('common.success'))
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || t('common.failed'))
    }
  }
}

const handleAdd = async () => {
  if (!newMember.userId) {
    ElMessage.warning(t('role.selectUser'))
    return
  }
  addLoading.value = true
  try {
    await virtualGroupApi.addMember(props.group.id, {
      userId: newMember.userId,
      role: newMember.role
    })
    showAddDialog.value = false
    await loadMembers()
    ElMessage.success(t('common.success'))
  } catch (error: any) {
    ElMessage.error(error.message || t('common.failed'))
  } finally {
    addLoading.value = false
  }
}
</script>

<style scoped>
.members-header { margin-bottom: 15px; }
</style>
