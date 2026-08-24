<template>
  <div class="portal-card my-bu-roles-card">
    <h2 class="section-title">
      {{ t('permission.myBuRoles') }}
    </h2>
    <el-empty
      v-if="myBuRoles.length === 0 && !loadingMyBuRoles"
      :description="t('permission.noMyBuRoles')"
    />
    <el-table
      v-else
      v-loading="loadingMyBuRoles"
      :data="myBuRoles"
      stripe
    >
      <el-table-column
        :label="t('permission.businessUnit')"
        min-width="160"
      >
        <template #default="{ row }">
          {{ row.businessUnitName || row.businessUnitId || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('permission.role')"
        min-width="140"
      >
        <template #default="{ row }">
          {{ row.roleName || row.roleId || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('permission.assignedAt')"
        width="180"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.assignedAt || row.createdAt) }}
        </template>
      </el-table-column>
    </el-table>
    <p class="table-foot-hint">
      {{ t('permission.requestRemoveBuRoleHint') }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMyBuRoles } from '@/composables/permissions/useMyBuRoles'
import { usePermissionFormatters } from '@/composables/permissions/usePermissionFormatters'

const { t } = useI18n()
const { formatDateTime } = usePermissionFormatters(t)
const { loadingMyBuRoles, myBuRoles, loadMyBuRoles } = useMyBuRoles()

onMounted(() => {
  loadMyBuRoles()
})

defineExpose({ reload: loadMyBuRoles })
</script>

<style lang="scss" scoped>
.my-bu-roles-card {
  margin-bottom: 16px;

  .section-title {
    margin: 0 0 12px;
    font-size: 16px;
    font-weight: 600;
  }

  .table-foot-hint {
    margin: 10px 0 0;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>
