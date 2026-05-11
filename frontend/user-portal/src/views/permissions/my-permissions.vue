<template>
  <div
    v-loading="loading"
    class="my-permissions-page"
  >
    <div class="page-header">
      <h1>{{ t('permission.permissionView') }}</h1>
    </div>

    <el-alert
      type="info"
      show-icon
      :closable="false"
      class="page-alert"
    >
      {{ t('permission.portalNoVirtualGroup') }}
    </el-alert>

    <div class="portal-card">
      <div class="section">
        <h3 class="section-title">
          {{ t('permission.sectionUbrTitle') }}
        </h3>
        <p class="section-desc">
          {{ t('permission.sectionUbrDesc') }}
        </p>
        <el-empty
          v-if="buBoundedRoles.length === 0"
          :description="t('profile.noBuRoleAssignments')"
        />
        <el-table
          v-else
          :data="ubrTableRows"
          :row-key="(r) => r.rowKey"
          stripe
          class="ubr-table"
        >
          <el-table-column
            prop="businessUnitName"
            :label="t('permission.businessUnit')"
            min-width="160"
          />
          <el-table-column
            prop="roleName"
            :label="t('permission.roleNameCol')"
            min-width="160"
          />
          <el-table-column
            prop="roleCode"
            :label="t('permission.roleCodeCol')"
            width="140"
          />
          <el-table-column
            :label="t('permission.activationCol')"
            width="120"
          >
            <template #default>
              <el-tag
                type="success"
                size="small"
              >
                {{ t('permission.activated') }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-divider />

      <div class="section">
        <h3 class="section-title">
          {{ t('permission.buUnbounded') }}
        </h3>
        <p class="section-desc">
          {{ t('permission.sectionUnboundedDesc') }}
        </p>
        <el-empty
          v-if="buUnboundedRoles.length === 0"
          :description="t('profile.noBuUnboundedRoles')"
        />
        <div
          v-else
          class="role-list"
        >
          <el-card
            v-for="role in buUnboundedRoles"
            :key="role.id"
            class="role-card"
            shadow="hover"
          >
            <div class="role-header">
              <span class="role-name">{{ role.name }}</span>
              <el-tag
                type="success"
                size="small"
              >
                {{ t('permission.buUnbounded') }}
              </el-tag>
            </div>
            <div class="role-status">
              <el-tag
                type="success"
                size="small"
              >
                {{ t('permission.activated') }}
              </el-tag>
              <span class="status-hint">{{ t('permission.effectivePermissions') }}</span>
            </div>
          </el-card>
        </div>
      </div>

      <el-divider />

      <div class="section">
        <h3 class="section-title">
          {{ t('permission.myBusinessUnits') }}
        </h3>
        <p class="section-desc">
          {{ t('permission.sectionBuMembershipDesc') }}
        </p>
        <el-empty
          v-if="businessUnits.length === 0"
          :description="t('permission.noBusinessUnits')"
        />
        <el-table
          v-else
          :data="businessUnits"
          stripe
        >
          <el-table-column
            prop="name"
            :label="t('permission.businessUnit')"
          />
          <el-table-column
            prop="joinedAt"
            :label="t('exitRole.joinTime')"
            width="180"
          >
            <template #default="{ row }">
              {{ formatDate(row.joinedAt) }}
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { permissionApi, type RoleInfo } from '@/api/permission'

const { t } = useI18n()

const loading = ref(false)

interface BuBoundedRoleItem {
  role: RoleInfo
  activatedBusinessUnits: { id: string; name: string }[]
}

const buUnboundedRoles = ref<RoleInfo[]>([])
const buBoundedRoles = ref<BuBoundedRoleItem[]>([])
const businessUnits = ref<{ id: string; name: string; joinedAt?: string }[]>([])

const ubrTableRows = computed(() => {
  const rows: { businessUnitName: string; roleName: string; roleCode: string; rowKey: string }[] = []
  for (const item of buBoundedRoles.value) {
    const bu = item.activatedBusinessUnits?.[0]
    const r = item.role
    rows.push({
      businessUnitName: bu?.name || '—',
      roleName: r?.name || '—',
      roleCode: r?.code || '—',
      rowKey: `${r?.id || ''}-${bu?.id || ''}`
    })
  }
  return rows
})

const formatDate = (dateStr?: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const loadPermissionView = async () => {
  loading.value = true
  try {
    const res = await permissionApi.getMyPermissionView()
    const data = res.data?.data || res.data || res
    buUnboundedRoles.value = data.buUnboundedRoles || []
    buBoundedRoles.value = data.buBoundedRoles || []
    businessUnits.value = data.businessUnits || []
  } catch (e) {
    console.error('Failed to load permission view:', e)
    try {
      const rolesRes = await permissionApi.getMyRoles()
      const roles = rolesRes.data?.data || rolesRes.data || []
      buUnboundedRoles.value = roles
        .filter((r: { roleType?: string }) => r.roleType === 'BU_UNBOUNDED')
        .map((r: { roleId: string; roleName?: string; name?: string }) => ({
          id: r.roleId,
          name: r.roleName || r.name
        }))
      buBoundedRoles.value = roles
        .filter((r: { roleType?: string }) => r.roleType === 'BU_BOUNDED')
        .map((r: any) => ({
          role: { id: r.roleId, name: r.roleName || r.name },
          activatedBusinessUnits: r.organizationUnitId
            ? [{ id: r.organizationUnitId, name: r.organizationUnitName || '' }]
            : []
        }))
    } catch (e2) {
      console.error('Fallback also failed:', e2)
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadPermissionView)
</script>

<style lang="scss" scoped>
.my-permissions-page {
  .page-header {
    margin-bottom: 16px;
    h1 {
      font-size: 24px;
      font-weight: 500;
      margin: 0;
    }
  }

  .page-alert {
    margin-bottom: 16px;
  }

  .section {
    margin-bottom: 8px;
    .section-title {
      font-size: 16px;
      font-weight: 500;
      margin-bottom: 8px;
      color: var(--text-primary);
    }
    .section-desc {
      font-size: 13px;
      color: var(--text-secondary);
      margin: 0 0 16px;
      line-height: 1.5;
    }
  }

  .ubr-table {
    width: 100%;
  }

  .role-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 16px;
  }

  .role-card {
    .role-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
      .role-name {
        font-weight: 500;
      }
    }
    .role-status {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
      .status-hint {
        color: var(--text-secondary);
        font-size: 13px;
      }
    }
  }
}
</style>
