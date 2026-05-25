<template>
  <el-dialog
    v-model="visible"
    :title="t('organization.approvers') + ' - ' + (businessUnit?.name || '')"
    width="600px"
    @open="onDialogOpen"
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
      <el-button
        type="primary"
        :disabled="!selectedUserId"
        @click="handleAddApprover"
      >
        {{ t('organization.addApprover') }}
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="approvers"
      stripe
      style="margin-top: 16px"
    >
      <el-table-column
        prop="userFullName"
        :label="t('user.fullName')"
      />
      <el-table-column
        prop="userName"
        :label="t('user.username')"
      />
      <el-table-column
        :label="t('common.operation')"
        width="100"
      >
        <template #default="{ row }">
          <el-button
            link
            type="danger"
            @click="handleRemoveApprover(row)"
          >
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-if="!loading && approvers.length === 0"
      :description="t('organization.noApprovers')"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import { watch, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useBusinessUnitApprovers } from '@/composables/modules/useBusinessUnitApprovers'
import type { BusinessUnit } from '@/api/businessUnit'

const props = defineProps<{ businessUnit: BusinessUnit | null }>()
const visible = defineModel<boolean>({ default: false })
const emit = defineEmits(['success'])
const { t } = useI18n()

const {
  loading, searchLoading, approvers, searchResults, selectedUserId,
  fetchApprovers, loadDefaultUsers, searchUsers, addApprover, removeApprover, resetDialog,
} = useBusinessUnitApprovers(toRef(props, 'businessUnit'))

const onDialogOpen = () => {
  fetchApprovers()
  resetDialog()
}

const handleAddApprover = async () => {
  if (await addApprover()) {
    emit('success')
  }
}

const handleRemoveApprover = async (row: Parameters<typeof removeApprover>[0]) => {
  if (await removeApprover(row)) {
    emit('success')
  }
}

watch(() => props.businessUnit, () => { if (visible.value) fetchApprovers() })
</script>

<style scoped>
.approvers-header { display: flex; gap: 12px; align-items: center; }
</style>
