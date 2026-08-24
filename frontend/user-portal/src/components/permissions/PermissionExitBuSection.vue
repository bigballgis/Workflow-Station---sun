<template>
  <div>
    <div class="portal-card exit-bu-card">
      <h2 class="section-title">
        {{ t('exitRole.title') }}
      </h2>
      <p class="section-sub">
        {{ t('exitRole.subtitle') }}
      </p>
      <el-alert
        type="info"
        show-icon
        :closable="false"
        class="info-alert"
      >
        {{ t('exitRole.portalNoVirtualGroup') }}
      </el-alert>
      <el-empty
        v-if="!loadingExitBu && exitBuRows.length === 0"
        :description="t('exitRole.noMemberships')"
      />
      <el-table
        v-else
        v-loading="loadingExitBu"
        :data="exitBuRows"
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
          width="180"
        >
          <template #default="{ row }">
            {{ formatDateTime(row.joinedAt) }}
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
      v-model="exitBuDialogVisible"
      :title="t('exitRole.requestExitBuTitle')"
      width="520px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item :label="t('permission.beneficiary')">
          <el-select
            v-model="exitBuForm.beneficiaryUserId"
            filterable
            remote
            clearable
            reserve-keyword
            :placeholder="t('permission.beneficiaryPlaceholder')"
            :remote-method="searchExitBuBeneficiaries"
            :loading="loadingExitBuBeneficiarySearch"
            style="width: 100%"
          >
            <el-option
              v-for="u in exitBuBeneficiaryOptions"
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
            v-model="exitBuForm.reason"
            type="textarea"
            :rows="3"
            :placeholder="t('permission.reasonPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exitBuDialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="exitBuSubmitting"
          @click="submitExitBu"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useExitBu } from '@/composables/permissions/useExitBu'
import { usePermissionFormatters } from '@/composables/permissions/usePermissionFormatters'

const emit = defineEmits<{
  'requests-changed': []
  'my-bu-roles-changed': []
}>()

const { t } = useI18n()
const { formatDateTime, beneficiaryOptionLabel } = usePermissionFormatters(t)

const reloadRequests = () => emit('requests-changed')
const reloadMyBu = () => emit('my-bu-roles-changed')

const {
  loadingExitBu,
  exitBuRows,
  exitBuDialogVisible,
  exitBuSubmitting,
  loadingExitBuBeneficiarySearch,
  exitBuBeneficiaryOptions,
  exitBuForm,
  loadExitBuMemberships,
  searchExitBuBeneficiaries,
  openExitBuDialog,
  submitExitBu,
} = useExitBu(t, {
  loadPendingRequests: reloadRequests,
  loadHistoryRequests: reloadRequests,
  loadMyBuRoles: reloadMyBu,
})

onMounted(() => {
  loadExitBuMemberships()
})

defineExpose({ reload: loadExitBuMemberships })
</script>

<style lang="scss" scoped>
.exit-bu-card {
  margin-bottom: 16px;

  .section-title {
    margin: 0 0 8px;
    font-size: 16px;
    font-weight: 600;
  }

  .section-sub {
    margin: 0 0 12px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  .info-alert {
    margin-bottom: 12px;
  }
}

.form-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
