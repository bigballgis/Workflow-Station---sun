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
      <el-button type="primary" @click="addApprover" :disabled="!selectedUserId">
        {{ t('virtualGroup.addApprover') }}
      </el-button>
    </div>

    <el-table :data="approvers" v-loading="loading" stripe style="margin-top: 16px">
      <el-table-column prop="userFullName" :label="t('user.fullName')" />
      <el-table-column prop="userName" :label="t('user.username')" />
      <el-table-column :label="t('common.operation')" width="100">
        <template #default="{ row }">
          <el-button link type="danger" @click="removeApprover(row)">
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && approvers.length === 0" :description="t('virtualGroup.noApprovers')" />
  </el-dialog>
</template>

<script setup lang="ts">
import { watch, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useVirtualGroupApprovers } from '@/composables/modules/useVirtualGroupApprovers'
import type { VirtualGroup } from '@/api/virtualGroup'

const props = defineProps<{ group: VirtualGroup | null }>()
const visible = defineModel<boolean>({ default: false })
const { t } = useI18n()

const { loading, searchLoading, approvers, searchResults, selectedUserId,
  fetchApprovers, loadDefaultUsers, searchUsers, addApprover, removeApprover, resetDialog }
  = useVirtualGroupApprovers(toRef(props, 'group'))

const handleDialogOpen = () => { fetchApprovers(); resetDialog() }
watch(() => props.group, () => { if (visible.value) fetchApprovers() })
</script>

<style scoped>
.approvers-header {
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>
