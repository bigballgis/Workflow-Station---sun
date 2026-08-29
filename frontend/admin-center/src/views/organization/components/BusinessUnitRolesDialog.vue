<template>
  <el-dialog
    v-model="visible"
    :title="t('organization.eligibleRoles') + ' - ' + (businessUnit?.name || '')"
    width="720px"
    @open="fetchRoles"
  >
    <div class="hint-text">
      <span>{{ t('organization.eligibleRolesDesc') }}</span>
      <DesignerHelpLink
        path="/up-tasks-to-claim#leader"
        :aria-label="t('organization.roleLeadersGuideLinkAria')"
        test-id="org-role-leaders-guide-link"
      />
    </div>
    <div
      v-if="!readOnly"
      class="roles-header"
    >
      <el-select
        v-model="selectedRoleId"
        :placeholder="t('common.selectPlaceholder')"
        filterable
        style="width: 300px"
      >
        <el-option
          v-for="role in availableRoles"
          :key="role.id"
          :label="role.name"
          :value="role.id"
        />
      </el-select>
      <el-button
        type="primary"
        :disabled="!selectedRoleId"
        @click="bindRole"
      >
        {{ t('common.add') }}
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="boundRoles"
      stripe
      style="margin-top: 16px"
    >
      <el-table-column
        prop="name"
        :label="t('role.roleName')"
      />
      <el-table-column
        prop="code"
        :label="t('role.roleCode')"
      />
      <el-table-column
        :label="t('organization.leaders')"
        min-width="180"
      >
        <template #default="{ row }">
          {{ roleLeaders[row.id] || t('organization.noLeaders') }}
        </template>
      </el-table-column>
      <el-table-column
        v-if="!readOnly"
        :label="t('common.operation')"
        width="100"
      >
        <template #default="{ row }">
          <el-button
            link
            type="danger"
            @click="unbindRole(row)"
          >
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>

<script setup lang="ts">
import { toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useBusinessUnitRoles } from '@/composables/modules/useBusinessUnitRoles'
import DesignerHelpLink from '@/components/relation-table/DesignerHelpLink.vue'
import type { BusinessUnit } from '@/api/businessUnit'

const props = defineProps<{ businessUnit: BusinessUnit | null; readOnly?: boolean }>()
const visible = defineModel<boolean>({ default: false })
const { t } = useI18n()

const { loading, boundRoles, availableRoles, selectedRoleId, roleLeaders, fetchRoles, bindRole, unbindRole }
  = useBusinessUnitRoles(toRef(props, 'businessUnit'))
</script>

<style scoped>
.hint-text {
  color: #909399;
  font-size: 13px;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.roles-header {
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>
