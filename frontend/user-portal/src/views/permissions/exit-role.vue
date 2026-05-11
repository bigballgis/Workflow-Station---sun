<template>
  <div class="exit-role-page">
    <div class="page-header">
      <h1>{{ t('exitRole.title') }}</h1>
      <p class="page-sub">
        {{ t('exitRole.subtitle') }}
      </p>
    </div>

    <el-alert
      type="info"
      show-icon
      :closable="false"
      class="info-alert"
    >
      {{ t('exitRole.portalNoVirtualGroup') }}
    </el-alert>

    <div class="portal-card">
      <el-empty
        v-if="!loading && memberships.businessUnits.length === 0"
        :description="t('exitRole.noMemberships')"
      />

      <el-table
        v-else
        v-loading="loading"
        :data="memberships.businessUnits"
        stripe
      >
        <el-table-column
          prop="businessUnitName"
          :label="t('exitRole.businessUnit')"
          min-width="200"
        />
        <el-table-column
          prop="joinedAt"
          :label="t('exitRole.joinTime')"
          width="160"
        >
          <template #default="{ row }">
            {{ formatDate(row.joinedAt) }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('common.actions')"
          width="200"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              type="danger"
              link
              size="small"
              @click="openExitBuDialog(row)"
            >
              {{ t('exitRole.requestExitBu') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="exitDialogVisible"
      :title="t('exitRole.requestExitBuTitle')"
      width="520px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item :label="t('permission.beneficiary')">
          <el-select
            v-model="exitForm.beneficiaryUserId"
            filterable
            remote
            clearable
            reserve-keyword
            :placeholder="t('permission.beneficiaryPlaceholder')"
            :remote-method="searchBeneficiaryUsers"
            :loading="loadingBeneficiarySearch"
            style="width: 100%"
          >
            <el-option
              v-for="u in beneficiaryOptions"
              :key="u.userId"
              :label="beneficiaryOptionLabel(u)"
              :value="u.userId"
            />
          </el-select>
          <div class="form-hint">
            {{ t('permission.beneficiaryHint') }}
          </div>
        </el-form-item>
        <el-form-item
          :label="t('permission.reason')"
          required
        >
          <el-input
            v-model="exitForm.reason"
            type="textarea"
            :rows="3"
            :placeholder="t('permission.reasonPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exitDialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="exitSubmitting"
          @click="submitExitBu"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { permissionApi } from '@/api/permission'
import { getStoredUser } from '@/api/auth'

const { t } = useI18n()

const loading = ref(false)
const exitDialogVisible = ref(false)
const exitSubmitting = ref(false)
const loadingBeneficiarySearch = ref(false)
const beneficiaryOptions = ref<{ userId: string; username: string; displayName?: string }[]>([])

interface BusinessUnitMembership {
  businessUnitId: string
  businessUnitName: string
  joinedAt?: string
}

const memberships = reactive<{
  virtualGroups: UserVirtualGroupMembership[]
  businessUnits: BusinessUnitMembership[]
}>({
  virtualGroups: [],
  businessUnits: []
})

const exitForm = reactive({
  businessUnitId: '',
  businessUnitName: '',
  beneficiaryUserId: '' as string,
  reason: ''
})

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const loadMemberships = async () => {
  loading.value = true
  try {
    const res = await permissionApi.getMyMemberships()
    const data = (res as any)?.data?.data || (res as any)?.data || res
    memberships.virtualGroups = []
    const buMap = new Map<string, BusinessUnitMembership>()
    if (data?.businessUnitRoles) {
      for (const role of data.businessUnitRoles) {
        if (!buMap.has(role.businessUnitId)) {
          buMap.set(role.businessUnitId, {
            businessUnitId: role.businessUnitId,
            businessUnitName: role.businessUnitName,
            joinedAt: role.assignedAt
          })
        }
      }
    }
    if (data?.businessUnits) {
      for (const bu of data.businessUnits) {
        const id = bu.businessUnitId || bu.id
        if (!buMap.has(id)) {
          buMap.set(id, {
            businessUnitId: id,
            businessUnitName: bu.businessUnitName || bu.name,
            joinedAt: bu.joinedAt
          })
        }
      }
    }
    memberships.businessUnits = Array.from(buMap.values())
  } catch (e) {
    console.error('Failed to load memberships:', e)
  } finally {
    loading.value = false
  }
}

const searchBeneficiaryUsers = async (query: string) => {
  loadingBeneficiarySearch.value = true
  try {
    const res = (await permissionApi.searchUsersForDelegation({
      keyword: query || undefined,
      page: 0,
      size: 20
    })) as any
    const payload = res?.data ?? res
    beneficiaryOptions.value = Array.isArray(payload?.content) ? payload.content : []
  } catch {
    beneficiaryOptions.value = []
  } finally {
    loadingBeneficiarySearch.value = false
  }
}

const beneficiaryOptionLabel = (u: { userId: string; username: string; displayName?: string }) => {
  const name = u.displayName || u.username || u.userId
  return `${u.username || u.userId}${name !== u.username ? ` · ${name}` : ''}`
}

const openExitBuDialog = (bu: BusinessUnitMembership) => {
  exitForm.businessUnitId = bu.businessUnitId
  exitForm.businessUnitName = bu.businessUnitName
  exitForm.beneficiaryUserId = ''
  exitForm.reason = ''
  beneficiaryOptions.value = []
  exitDialogVisible.value = true
}

const submitExitBu = async () => {
  if (!exitForm.reason.trim()) {
    ElMessage.warning(t('permission.enterReason'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('exitRole.exitBuConfirm', { bu: exitForm.businessUnitName || exitForm.businessUnitId }),
      t('common.confirm'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  exitSubmitting.value = true
  try {
    const body: { businessUnitId: string; reason: string; beneficiaryUserId?: string } = {
      businessUnitId: exitForm.businessUnitId,
      reason: exitForm.reason.trim()
    }
    const me = getStoredUser()?.userId
    if (exitForm.beneficiaryUserId && exitForm.beneficiaryUserId !== me) {
      body.beneficiaryUserId = exitForm.beneficiaryUserId
    }
    await permissionApi.requestBusinessUnitExit(body)
    ElMessage.success(t('exitRole.exitRequestSuccess'))
    exitDialogVisible.value = false
    loadMemberships()
  } catch (e: any) {
    ElMessage.error(e.message || t('exitRole.exitFailed'))
  } finally {
    exitSubmitting.value = false
  }
}

onMounted(loadMemberships)
</script>

<style lang="scss" scoped>
.exit-role-page {
  .page-header {
    margin-bottom: 12px;
    h1 {
      font-size: 24px;
      font-weight: 500;
      margin: 0 0 8px;
    }
    .page-sub {
      margin: 0;
      color: var(--el-text-color-secondary);
      font-size: 14px;
    }
  }
  .info-alert {
    margin-bottom: 16px;
  }
  .form-hint {
    margin-top: 6px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.4;
  }
}
</style>
