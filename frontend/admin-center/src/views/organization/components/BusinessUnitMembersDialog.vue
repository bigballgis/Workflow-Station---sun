<template>
  <el-dialog
    v-model="visible"
    :title="t('organization.members') + ' - ' + (businessUnit?.name || '')"
    width="700px"
    @open="onDialogOpen"
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
      <el-button
        type="primary"
        :disabled="!selectedUserId"
        @click="addMember"
      >
        {{ t('role.addMember') }}
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="members"
      stripe
      style="margin-top: 16px"
      max-height="400"
    >
      <el-table-column
        prop="username"
        :label="t('user.username')"
        width="120"
      />
      <el-table-column
        prop="fullName"
        :label="t('user.fullName')"
        width="120"
      />
      <el-table-column
        prop="email"
        :label="t('user.email')"
      />
      <el-table-column
        :label="t('common.operation')"
        width="100"
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

    <el-empty
      v-if="!loading && members.length === 0"
      :description="t('common.noData')"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import { watch, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useBusinessUnitMembers } from '@/composables/modules/useBusinessUnitMembers'
import type { BusinessUnit } from '@/api/businessUnit'

const props = defineProps<{ businessUnit: BusinessUnit | null }>()
const visible = defineModel<boolean>({ default: false })
const emit = defineEmits(['success'])
const { t } = useI18n()

const {
  loading, searchLoading, members, searchResults, selectedUserId,
  fetchMembers, loadDefaultUsers, searchUsers, addMember, removeMember, resetDialog,
} = useBusinessUnitMembers(toRef(props, 'businessUnit'))

const onDialogOpen = () => {
  fetchMembers()
  resetDialog()
}

watch(() => props.businessUnit, () => { if (visible.value) fetchMembers() })
</script>

<style scoped>
.members-header { display: flex; gap: 12px; align-items: center; }
</style>
