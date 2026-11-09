<template>
  <el-dialog
    :model-value="modelValue"
    :title="`${t('virtualGroup.members')} - ${group?.name}`"
    width="1100px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div
      v-if="!readOnly"
      class="members-header"
    >
      <el-button
        type="primary"
        size="small"
        @click="openAddDialog"
      >
        {{ t('role.addMember') }}
      </el-button>
    </div>

    <!-- A team has a Developer and a Technical Lead list (platform access model 5.4) -->
    <el-tabs
      v-if="lists.length > 1"
      v-model="activeList"
    >
      <el-tab-pane
        v-for="list in lists"
        :key="list"
        :label="`${listLabel(list)} (${countOf(list)})`"
        :name="list"
      />
    </el-tabs>

    <el-table
      v-loading="loading"
      :data="listMembers"
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
        prop="sources"
        :label="t('virtualGroup.source')"
        min-width="130"
      >
        <template #default="{ row }">
          <el-tag
            v-for="source in row.sources"
            :key="source"
            :type="source === 'AD' ? 'primary' : 'info'"
            size="small"
            class="source-tag"
          >
            {{ source === 'AD' ? t('virtualGroup.sourceAd') : t('virtualGroup.sourceManual') }}
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
        v-if="!readOnly"
        :label="t('common.operation')"
        min-width="130"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-if="row.sources.includes('MANUAL')"
            link
            type="danger"
            @click="revokeManual(row)"
          >
            {{ t('virtualGroup.revokeManual') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="members-note">
      {{ t('virtualGroup.adMembersNote') }}
    </div>

    <el-dialog
      v-model="showAddDialog"
      :title="t('role.addMember')"
      width="440px"
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
        <el-form-item :label="t('virtualGroup.list')">
          <el-select
            v-model="newMember.listType"
            style="width: 100%"
            :disabled="lists.length === 1"
          >
            <el-option
              v-for="list in lists"
              :key="list"
              :label="listLabel(list)"
              :value="list"
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
import type { VirtualGroup } from '@/api/virtualGroup'

const props = defineProps<{ modelValue: boolean; group: VirtualGroup | null; readOnly?: boolean }>()
const emit = defineEmits(['update:modelValue'])
const { t } = useI18n()

const { loading, showAddDialog, addLoading, searchLoading, userOptions, newMember,
  lists, activeList, listMembers, listLabel, countOf,
  loadMembers, openAddDialog, loadDefaultUsers, searchUsers, addMember, revokeManual }
  = useVirtualGroupMembers(toRef(props, 'group'))

watch(() => props.modelValue, async (val) => { if (val && props.group) await loadMembers() })
</script>

<style scoped>
.members-header { margin-bottom: 15px; }
.source-tag + .source-tag { margin-left: 6px; }
.members-note {
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
