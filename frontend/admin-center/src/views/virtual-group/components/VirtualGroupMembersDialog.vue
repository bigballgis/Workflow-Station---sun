<template>
  <el-dialog
    :model-value="modelValue"
    :title="`${t('virtualGroup.members')} - ${group?.name}`"
    width="1100px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="members-header">
      <el-button
        type="primary"
        size="small"
        @click="openAddDialog"
      >
        {{ t('role.addMember') }}
      </el-button>
    </div>
    
    <el-table
      v-loading="loading"
      :data="members"
      max-height="400"
      table-layout="auto"
      style="width: 100%"
    >
      <!-- <el-table-column prop="employeeId" :label="t('user.employeeId')" min-width="110" show-overflow-tooltip /> -->
      <el-table-column
        prop="fullName"
        :label="t('user.fullName')"
        min-width="130"
        show-overflow-tooltip
      />
      <el-table-column
        prop="username"
        :label="t('user.username')"
        min-width="120"
        show-overflow-tooltip
      />
      <el-table-column
        prop="email"
        :label="t('user.email')"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        prop="role"
        :label="t('user.role')"
        min-width="110"
      >
        <template #default="{ row }">
          <el-tag
            type="info"
            size="small"
          >
            {{ t('role.members') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="joinedAt"
        :label="t('common.createTime')"
        min-width="170"
      >
        <template #default="{ row }">
          {{ row.joinedAt ? new Date(row.joinedAt).toLocaleString('zh-CN') : '-' }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('common.operation')"
        min-width="80"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            type="danger"
            @click="removeMember(row)"
          >
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <el-dialog
      v-model="showAddDialog"
      :title="t('role.addMember')"
      width="400px"
      append-to-body
    >
      <el-form
        label-width="auto"
        label-position="left"
      >
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
          <el-select
            v-model="newMember.role"
            style="width: 100%"
            disabled
          >
            <el-option
              :label="t('role.members')"
              value="MEMBER"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="addLoading"
          @click="addMember"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { watch, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useVirtualGroupMembers } from '@/composables/modules/useVirtualGroupMembers'

const props = defineProps<{ modelValue: boolean; group: any }>()
const emit = defineEmits(['update:modelValue'])
const { t } = useI18n()

const { loading, members, showAddDialog, addLoading, searchLoading, userOptions, newMember,
  loadMembers, openAddDialog, loadDefaultUsers, searchUsers, addMember, removeMember }
  = useVirtualGroupMembers(toRef(props, 'group'))

watch(() => props.modelValue, async (val) => { if (val && props.group) await loadMembers() })
</script>

<style scoped>
.members-header { margin-bottom: 15px; }
</style>
