<template>
  <div class="my-permissions-page">
    <div class="page-header">
      <h1>{{ t('permission.permissionView') }}</h1>
    </div>

    <el-tabs v-model="activeTab">
      <!-- 我的角色 -->
      <el-tab-pane :label="t('permission.myRoles')" name="roles">
        <div class="portal-card">
          <div class="section">
            <h3 class="section-title">{{ t('permission.buUnbounded') }}</h3>
            <el-empty v-if="buUnboundedRoles.length === 0" :description="t('permission.noRoles')" />
            <div v-else class="role-list">
              <el-card v-for="role in buUnboundedRoles" :key="role.id" class="role-card" shadow="hover">
                <div class="role-header">
                  <span class="role-name">{{ role.name }}</span>
                  <el-tag type="success" size="small">{{ t('permission.buUnbounded') }}</el-tag>
                </div>
                <div class="role-status">
                  <el-tag type="success" size="small">{{ t('permission.activated') }}</el-tag>
                  <span class="status-hint">{{ t('permission.effectivePermissions') }}</span>
                </div>
              </el-card>
            </div>
          </div>

          <el-divider />

          <div class="section">
            <h3 class="section-title">{{ t('permission.buBounded') }}</h3>
            <el-empty v-if="buBoundedRoles.length === 0" :description="t('permission.noRoles')" />
            <div v-else class="role-list">
              <el-card v-for="item in buBoundedRoles" :key="item.role.id" class="role-card" shadow="hover">
                <div class="role-header">
                  <span class="role-name">{{ item.role.name }}</span>
                  <el-tag type="warning" size="small">{{ t('permission.buBounded') }}</el-tag>
                </div>
                <div class="role-status">
                  <template v-if="item.activatedBusinessUnits.length > 0">
                    <el-tag type="success" size="small">{{ t('permission.activated') }}</el-tag>
                    <span class="status-hint">{{ t('permission.activatedIn') }}:</span>
                    <el-tag v-for="bu in item.activatedBusinessUnits" :key="bu.id" size="small" type="info" class="bu-tag">
                      {{ bu.name }}
                    </el-tag>
                  </template>
                  <template v-else>
                    <el-tag type="danger" size="small">{{ t('permission.notActivated') }}</el-tag>
                    <el-button type="primary" size="small" link @click="goToApplyBusinessUnit">
                      {{ t('reminder.goToApply') }}
                    </el-button>
                  </template>
                </div>
              </el-card>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 我的虚拟组 -->
      <el-tab-pane :label="t('permission.myVirtualGroups')" name="virtualGroups">
        <div class="portal-card">
          <el-empty v-if="virtualGroups.length === 0" :description="t('permission.noVirtualGroups')" />
          <div v-else class="virtual-group-list">
            <el-card v-for="group in virtualGroups" :key="group.groupId" class="group-card" shadow="hover">
              <template #header>
                <div class="group-header">
                  <span class="group-name">{{ group.groupName }}</span>
                  <el-tag size="small" type="success">{{ t('permission.member') }}</el-tag>
                </div>
              </template>
              <div v-if="group.boundRoles && group.boundRoles.length > 0" class="bound-roles">
                <span class="label">{{ t('permission.boundRoles') }}:</span>
                <div v-for="role in group.boundRoles" :key="role.id" class="role-item">
                  <el-tag size="small" type="info">{{ role.name }}</el-tag>
                  <el-tag size="small" :type="role.type === 'BU_BOUNDED' ? 'warning' : 'success'">
                    {{ role.type === 'BU_BOUNDED' ? t('permission.buBounded') : t('permission.buUnbounded') }}
                  </el-tag>
                </div>
              </div>
              <div v-else class="no-bound-roles">{{ t('permission.noBoundRoles') }}</div>
            </el-card>
          </div>
        </div>
      </el-tab-pane>

      <!-- 我的业务单元 -->
      <el-tab-pane :label="t('permission.myBusinessUnits')" name="businessUnits">
        <div class="portal-card">
          <el-empty v-if="businessUnits.length === 0" :description="t('permission.noBusinessUnits')" />
          <el-table v-else :data="businessUnits" stripe>
            <el-table-column prop="name" :label="t('permission.businessUnit')" />
            <el-table-column prop="joinedAt" :label="t('exitRole.joinTime')" width="180">
              <template #default="{ row }">{{ formatDate(row.joinedAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { permissionApi, type UserVirtualGroupMembership, type RoleInfo } from '@/api/permission'

const { t } = useI18n()
const router = useRouter()

const activeTab = ref('roles')
const loading = ref(false)

interface BuBoundedRoleItem {
  role: RoleInfo
  activatedBusinessUnits: { id: string; name: string }[]
}

const buUnboundedRoles = ref<RoleInfo[]>([])
const buBoundedRoles = ref<BuBoundedRoleItem[]>([])
const virtualGroups = ref<UserVirtualGroupMembership[]>([])
const businessUnits = ref<{ id: string; name: string; joinedAt?: string }[]>([])

const formatDate = (dateStr?: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

const loadPermissionView = async () => {
  loading.value = true
  try {
    const res = await permissionApi.getMyPermissionView()
    const data = (res as any).data || res
    if (data) {
      buUnboundedRoles.value = data.buUnboundedRoles || []
      buBoundedRoles.value = data.buBoundedRoles || []
      virtualGroups.value = data.virtualGroups || []
      businessUnits.value = data.businessUnits || []
    }
  } catch (e) {
    console.error('Failed to load permission view:', e)
    // Fallback to old API
    try {
      const [rolesRes, groupsRes] = await Promise.all([
        permissionApi.getMyRoles(),
        permissionApi.getMyVirtualGroups()
      ])
      const roles = (rolesRes as any).data || []
      virtualGroups.value = (groupsRes as any).data || []
      // Separate roles by type
      buUnboundedRoles.value = Array.isArray(roles) ? roles.filter((r: any) => r.roleType === 'BU_UNBOUNDED').map((r: any) => ({
        id: r.roleId || r.id,
        name: r.roleName || r.name,
        code: r.roleCode || r.code || r.roleId || r.id,
        description: r.description,
        type: r.roleType || r.type
      })) : []
      buBoundedRoles.value = []
      businessUnits.value = []
      if (Array.isArray(roles)) {
        buBoundedRoles.value = roles.filter((r: any) => r.roleType === 'BU_BOUNDED').map((r: any) => ({
          role: {
            id: r.roleId || r.id,
            name: r.roleName || r.name,
            code: r.roleCode || r.code || r.roleId || r.id,
            description: r.description,
            type: r.roleType || r.type
          },
          activatedBusinessUnits: r.organizationUnitId ? [{ id: r.organizationUnitId, name: r.organizationUnitName || r.organizationUnitId }] : []
        }))
      }
    } catch (e2) {
      console.error('Fallback also failed:', e2)
    }
  } finally {
    loading.value = false
  }
}

const goToApplyBusinessUnit = () => {
  router.push('/permissions')
}

onMounted(loadPermissionView)
</script>

<style lang="scss" scoped>
.my-permissions-page {
  .page-header {
    margin-bottom: 20px;
    h1 { font-size: 24px; font-weight: 500; margin: 0; }
  }

  .section {
    margin-bottom: 20px;
    .section-title {
      font-size: 16px;
      font-weight: 500;
      margin-bottom: 16px;
      color: var(--text-primary);
    }
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
      .role-name { font-weight: 500; }
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
      .bu-tag { margin-right: 4px; }
    }
  }

  .virtual-group-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 16px;
  }

  .group-card {
    .group-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      .group-name { font-weight: 500; }
    }
    .bound-roles {
      .label {
        color: var(--text-secondary);
        font-size: 13px;
        margin-right: 8px;
      }
      .role-item {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        margin-right: 8px;
        margin-bottom: 4px;
      }
    }
    .no-bound-roles {
      color: var(--text-secondary);
      font-size: 13px;
    }
  }
}
</style>
