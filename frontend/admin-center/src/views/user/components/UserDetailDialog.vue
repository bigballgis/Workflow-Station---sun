<template>
  <el-dialog 
    :model-value="modelValue" 
    @update:model-value="$emit('update:modelValue', $event)" 
    :title="t('common.view')" 
    width="820px"
    destroy-on-close
  >
    <div v-loading="loading" class="user-detail">
      <template v-if="user">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('user.username')">{{ user.username }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.fullName')">{{ user.fullName }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.email')">{{ user.email }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.employeeId')">{{ user.employeeId || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.position')">{{ user.position || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.status')">
            <el-tag :type="statusType(user.status)" size="small">{{ statusText(user.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('user.entityManager')">{{ user.entityManagerName || t('user.notSet') }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.functionManager')">{{ user.functionManagerName || t('user.notSet') }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.createTime')">{{ formatDate(user.createdAt) }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.lastLogin')">{{ user.lastLoginAt ? formatDate(user.lastLoginAt) : '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.lastLoginIp')" :span="2">{{ user.lastLoginIp || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="detailActiveTab" class="detail-tabs">
          <el-tab-pane :label="t('user.detailTabPortalOrg')" name="portal">
            <p class="tab-lead">{{ t('user.portalOrgTabHint') }}</p>

            <div class="section-title">{{ t('user.businessUnits') }}</div>
            <el-table :data="businessUnits" border size="small" v-if="businessUnits.length">
              <el-table-column prop="name" :label="t('businessUnit.name')" />
              <el-table-column prop="code" :label="t('businessUnit.code')" width="150" />
              <el-table-column prop="path" :label="t('businessUnit.path')" show-overflow-tooltip />
            </el-table>
            <el-empty v-else :description="t('user.noBusinessUnits')" :image-size="60" />
            <div class="section-hint">{{ t('user.businessUnitHint') }}</div>

            <div class="section-title">{{ t('user.portalVirtualGroupsSection') }}</div>
            <el-table :data="portalVirtualGroups" border size="small" v-if="portalVirtualGroups.length">
              <el-table-column prop="groupName" :label="t('virtualGroup.name')" />
              <el-table-column prop="groupDescription" :label="t('common.description')" />
              <el-table-column prop="joinedAt" :label="t('user.joinedAt')" width="170">
                <template #default="{ row }">{{ formatDate(row.joinedAt) }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-else :description="t('user.noPortalVirtualGroups')" :image-size="60" />
            <div class="section-hint">{{ t('user.portalVirtualGroupHint') }}</div>

            <div class="section-title">{{ t('user.buRoleAssignments') }}</div>
            <div class="ubr-toolbar">
              <el-button type="primary" size="small" @click="openAssignBuRole">
                {{ t('user.assignBuRole') }}
              </el-button>
            </div>
            <template v-if="buRoleGroups.length">
              <div v-for="g in buRoleGroups" :key="g.businessUnitId" class="ubr-group">
                <div class="ubr-group-title">{{ g.businessUnitName }}</div>
                <el-table :data="g.rows" border size="small">
                  <el-table-column prop="roleName" :label="t('user.roleName')" />
                  <el-table-column prop="roleCode" :label="t('user.roleCode')" width="160" />
                  <el-table-column :label="t('common.operation')" width="100" align="center">
                    <template #default="{ row }">
                      <el-button type="danger" link size="small" @click="handleRemoveBuRole(row)">
                        {{ t('user.removeBuRole') }}
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
            <el-empty v-else :description="t('user.noBuRoles')" :image-size="60" />
            <div class="section-hint">{{ t('user.buRoleHint') }}</div>
          </el-tab-pane>

          <el-tab-pane :label="t('user.detailTabPlatform')" name="platform">
            <p class="tab-lead">{{ t('user.platformAccessTabHint') }}</p>

            <div class="section-title">{{ t('user.platformVirtualGroupsSection') }}</div>
            <el-table :data="platformVirtualGroups" border size="small" v-if="platformVirtualGroups.length">
              <el-table-column prop="groupName" :label="t('virtualGroup.name')" />
              <el-table-column prop="groupDescription" :label="t('common.description')" />
              <el-table-column prop="joinedAt" :label="t('user.joinedAt')" width="170">
                <template #default="{ row }">{{ formatDate(row.joinedAt) }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-else :description="t('user.noPlatformVirtualGroups')" :image-size="60" />
            <div class="section-hint">{{ t('user.platformVirtualGroupHint') }}</div>

            <div class="section-title">{{ t('user.platformRolesSection') }}</div>
            <el-table :data="platformRoles" border size="small" v-if="platformRoles.length">
              <el-table-column prop="name" :label="t('user.roleName')" />
              <el-table-column prop="code" :label="t('user.roleCode')" width="160" />
              <el-table-column prop="type" :label="t('user.roleTypeColumn')" width="130">
                <template #default="{ row }">
                  <el-tag size="small" :type="getPlatformRoleTagType(row.type)">{{ row.type || '—' }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else :description="t('user.noPlatformRoles')" :image-size="60" />
          </el-tab-pane>
        </el-tabs>

        <!-- 登录历史 -->
        <div class="section-title">{{ t('user.loginHistory') }}</div>
        <el-table :data="user.loginHistory" border size="small" max-height="200" v-if="user.loginHistory?.length">
          <el-table-column prop="loginTime" :label="t('user.loginTime')" width="170">
            <template #default="{ row }">{{ formatDate(row.loginTime) }}</template>
          </el-table-column>
          <el-table-column prop="ipAddress" :label="t('user.ipAddress')" width="140" />
          <el-table-column prop="success" :label="t('user.loginStatus')" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                {{ row.success ? t('common.success') : t('common.failed') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="failureReason" :label="t('user.failureReason')" />
        </el-table>
        <el-empty v-else :description="t('user.noLoginHistory')" :image-size="60" />
      </template>
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">{{ t('common.close') }}</el-button>
      <el-button type="warning" @click="handleResetPassword">{{ t('user.resetPassword') }}</el-button>
    </template>

    <el-dialog
      v-model="assignDialogVisible"
      :title="t('user.assignBuRole')"
      width="480px"
      destroy-on-close
      append-to-body
      @closed="resetAssignDialog"
    >
      <el-form label-width="110px">
        <el-form-item :label="t('user.businessUnit')">
          <el-select
            v-model="assignForm.businessUnitId"
            filterable
            class="ubr-select"
            @change="onAssignBuChange"
          >
            <el-option
              v-for="bu in businessUnits"
              :key="bu.id"
              :label="bu.name"
              :value="bu.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('user.role')">
          <el-select
            v-model="assignForm.roleId"
            filterable
            class="ubr-select"
            :loading="assignRoleLoading"
            :placeholder="t('user.selectRoleForBu')"
          >
            <el-option
              v-for="r in assignRoleOptions"
              :key="r.id"
              :label="`${r.name} (${r.code})`"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <p v-if="assignRoleLoaded && !assignRoleOptions.length" class="ubr-empty-hint">
        {{ t('user.noEligibleRolesForBu') }}
      </p>
      <template #footer>
        <el-button @click="assignDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="assignSubmitting" @click="submitAssignBuRole">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  userApi,
  type UserDetail,
  type UserBusinessUnitMembership,
  type UserVirtualGroupMembership,
  type UserBusinessUnitRole
} from '@/api/user'
import { listAssignableBuBoundedRoles, type BuBoundedRole } from '@/api/taskAssignment'

const { t } = useI18n()

const props = defineProps<{ modelValue: boolean; userId: string }>()
defineEmits(['update:modelValue'])

const loading = ref(false)
const detailActiveTab = ref<'portal' | 'platform'>('portal')
const user = ref<UserDetail | null>(null)
const businessUnits = ref<UserBusinessUnitMembership[]>([])
const portalVirtualGroups = ref<UserVirtualGroupMembership[]>([])
const platformVirtualGroups = ref<UserVirtualGroupMembership[]>([])
const platformRoles = ref<{ id: string; name: string; code: string; type: string }[]>([])
const buRoles = ref<UserBusinessUnitRole[]>([])

const getPlatformRoleTagType = (type?: string) => {
  if (type === 'BU_BOUNDED') return 'warning'
  if (type === 'BU_UNBOUNDED') return 'success'
  if (type === 'ADMIN') return 'danger'
  if (type === 'DEVELOPER') return 'primary'
  return 'info'
}

const assignDialogVisible = ref(false)
const assignRoleLoading = ref(false)
const assignSubmitting = ref(false)
const assignRoleOptions = ref<BuBoundedRole[]>([])
const assignRoleLoaded = ref(false)
const assignForm = reactive({ businessUnitId: '', roleId: '' })

const buRoleGroups = computed(() => {
  const map = new Map<
    string,
    { businessUnitId: string; businessUnitName: string; rows: UserBusinessUnitRole[] }
  >()
  for (const r of buRoles.value) {
    const key = r.businessUnitId
    if (!map.has(key)) {
      map.set(key, {
        businessUnitId: key,
        businessUnitName: r.businessUnitName || key,
        rows: []
      })
    }
    map.get(key)!.rows.push(r)
  }
  return [...map.values()]
})

const statusType = (status: string): 'success' | 'info' | 'danger' | 'warning' => {
  const map: Record<string, 'success' | 'info' | 'danger' | 'warning'> = { ACTIVE: 'success', DISABLED: 'info', LOCKED: 'danger', PENDING: 'warning' }
  return map[status] || 'info'
}

const statusText = (status: string) => {
  const map: Record<string, string> = { 
    ACTIVE: t('user.active'), 
    DISABLED: t('user.disabled'), 
    LOCKED: t('user.locked'), 
    PENDING: t('user.pending') 
  }
  return map[status] || status
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

const reloadBuRoles = async () => {
  if (!props.userId) return
  buRoles.value = await userApi.getBusinessUnitRoles(props.userId)
}

const resetAssignDialog = () => {
  assignForm.businessUnitId = ''
  assignForm.roleId = ''
  assignRoleOptions.value = []
  assignRoleLoaded.value = false
}

const onAssignBuChange = async () => {
  const buId = assignForm.businessUnitId
  assignForm.roleId = ''
  assignRoleOptions.value = []
  assignRoleLoaded.value = false
  if (!buId) return
  assignRoleLoading.value = true
  try {
    const assignable = await listAssignableBuBoundedRoles(buId)
    const taken = new Set(
      buRoles.value.filter((r) => r.businessUnitId === buId).map((r) => r.roleId)
    )
    assignRoleOptions.value = assignable.filter((r) => !taken.has(r.id))
  } catch (error: any) {
    ElMessage.error(error.message || t('common.failed'))
  } finally {
    assignRoleLoading.value = false
    assignRoleLoaded.value = true
  }
}

const openAssignBuRole = async () => {
  if (!businessUnits.value.length) {
    ElMessage.warning(t('user.assignBuRoleNeedMembership'))
    return
  }
  resetAssignDialog()
  assignForm.businessUnitId = businessUnits.value[0]!.id
  assignDialogVisible.value = true
  await onAssignBuChange()
}

const submitAssignBuRole = async () => {
  if (!user.value || !assignForm.businessUnitId || !assignForm.roleId) {
    // #region agent log
    fetch('http://127.0.0.1:7683/ingest/1fc88847-d32b-4694-9f56-a337ecc92dd3', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': '864cd4' },
      body: JSON.stringify({
        sessionId: '864cd4',
        hypothesisId: 'H_silent_noop',
        location: 'UserDetailDialog.vue:submitAssignBuRole',
        message: 'early exit missing bu or role',
        data: {
          hasUser: !!user.value,
          businessUnitId: assignForm.businessUnitId || '',
          roleId: assignForm.roleId || ''
        },
        timestamp: Date.now()
      })
    }).catch(() => {})
    // #endregion
    ElMessage.warning(t('user.selectRoleForBu'))
    return
  }
  assignSubmitting.value = true
  try {
    // #region agent log
    fetch('http://127.0.0.1:7683/ingest/1fc88847-d32b-4694-9f56-a337ecc92dd3', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': '864cd4' },
      body: JSON.stringify({
        sessionId: '864cd4',
        hypothesisId: 'H_api_call',
        location: 'UserDetailDialog.vue:submitAssignBuRole',
        message: 'before assignBusinessUnitRole',
        data: {
          userId: user.value.id,
          businessUnitId: assignForm.businessUnitId,
          roleId: assignForm.roleId
        },
        timestamp: Date.now()
      })
    }).catch(() => {})
    // #endregion
    await userApi.assignBusinessUnitRole(user.value.id, assignForm.businessUnitId, assignForm.roleId)
    // #region agent log
    fetch('http://127.0.0.1:7683/ingest/1fc88847-d32b-4694-9f56-a337ecc92dd3', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': '864cd4' },
      body: JSON.stringify({
        sessionId: '864cd4',
        hypothesisId: 'H_api_ok',
        location: 'UserDetailDialog.vue:submitAssignBuRole',
        message: 'assignBusinessUnitRole resolved',
        data: { userId: user.value.id },
        timestamp: Date.now()
      })
    }).catch(() => {})
    // #endregion
    ElMessage.success(t('common.success'))
    assignDialogVisible.value = false
    await reloadBuRoles()
  } catch (error: any) {
    // #region agent log
    fetch('http://127.0.0.1:7683/ingest/1fc88847-d32b-4694-9f56-a337ecc92dd3', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': '864cd4' },
      body: JSON.stringify({
        sessionId: '864cd4',
        hypothesisId: 'H_api_err',
        location: 'UserDetailDialog.vue:submitAssignBuRole',
        message: 'assignBusinessUnitRole rejected',
        data: { err: String(error?.message ?? error) },
        timestamp: Date.now()
      })
    }).catch(() => {})
    // #endregion
    ElMessage.error(error.message || t('common.failed'))
  } finally {
    assignSubmitting.value = false
  }
}

const handleRemoveBuRole = async (row: UserBusinessUnitRole) => {
  if (!user.value) return
  const roleLabel = row.roleName || row.roleCode || row.roleId
  try {
    await ElMessageBox.confirm(
      t('user.confirmRemoveBuRole', { role: roleLabel }),
      t('common.confirm'),
      { type: 'warning' }
    )
    await userApi.removeBusinessUnitRole(user.value.id, row.businessUnitId, row.roleId)
    ElMessage.success(t('common.success'))
    await reloadBuRoles()
  } catch (error: any) {
    if (error !== 'cancel') ElMessage.error(error.message || t('common.failed'))
  }
}

watch(() => props.modelValue, async (val) => {
  if (val && props.userId) {
    detailActiveTab.value = 'portal'
    loading.value = true
    try {
      const [userData, buData, portalVg, platformVg, platRoles, ubrData] = await Promise.all([
        userApi.getById(props.userId),
        userApi.getBusinessUnits(props.userId),
        userApi.getVirtualGroups(props.userId, 'PORTAL'),
        userApi.getVirtualGroups(props.userId, 'ADMIN'),
        userApi.getRoles(props.userId, 'ADMIN'),
        userApi.getBusinessUnitRoles(props.userId)
      ])
      user.value = userData
      businessUnits.value = buData
      portalVirtualGroups.value = portalVg
      platformVirtualGroups.value = platformVg
      platformRoles.value = platRoles || []
      buRoles.value = ubrData
    } catch (error: any) {
      ElMessage.error(error.message || t('common.failed'))
    } finally {
      loading.value = false
    }
  }
})

const handleResetPassword = async () => {
  if (!user.value) return
  try {
    await ElMessageBox.confirm(t('user.resetPassword') + ` - ${user.value.fullName}?`, t('common.confirm'), { type: 'warning' })
    await userApi.resetPassword(user.value.id)
    ElMessage.success(t('user.passwordResetNoPlaintext'))
  } catch (error: any) {
    if (error !== 'cancel') ElMessage.error(error.message || t('common.failed'))
  }
}
</script>

<style scoped lang="scss">
.user-detail {
  min-height: 200px;

  .detail-tabs {
    margin-top: 8px;
    :deep(.el-tabs__header) {
      margin-bottom: 12px;
    }
  }

  .tab-lead {
    font-size: 12px;
    color: #909399;
    margin: 0 0 12px;
    line-height: 1.5;
  }

  .section-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    margin: 20px 0 12px;
    padding-left: 8px;
    border-left: 3px solid #DB0011;
  }
  .section-hint {
    font-size: 12px;
    color: #909399;
    margin-top: 8px;
    padding-left: 8px;
  }
  .ubr-toolbar {
    margin-bottom: 12px;
    padding-left: 8px;
  }
  .ubr-group {
    margin-bottom: 16px;
    padding-left: 8px;
  }
  .ubr-group-title {
    font-size: 13px;
    font-weight: 600;
    color: #606266;
    margin-bottom: 8px;
  }
  .ubr-select {
    width: 100%;
  }
}
.ubr-empty-hint {
  margin: 0 0 8px;
  font-size: 13px;
  color: #909399;
}
</style>
