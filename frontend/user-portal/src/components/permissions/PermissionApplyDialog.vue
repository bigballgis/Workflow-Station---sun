<template>
    <el-dialog
      v-model="applyDialogVisible"
      :title="t('permission.applyPermission')"
      width="600px"
    >
      <el-form
        :model="applyForm"
        label-width="auto"
        label-position="left"
        class="apply-form"
      >
        <!-- 申请类型选择 -->
        <el-form-item :label="t('permission.applyType')">
          <el-tag
            type="primary"
            size="large"
          >
            {{ t('permission.joinBusinessUnit') }}
          </el-tag>
        </el-form-item>

        <el-form-item :label="t('permission.beneficiary')">
          <el-select
            v-model="applyForm.beneficiaryUserId"
            filterable
            remote
            clearable
            reserve-keyword
            :placeholder="t('permission.beneficiaryPlaceholder')"
            :remote-method="searchBeneficiaryUsers"
            :loading="loadingBeneficiarySearch"
            style="width: 100%"
            :teleported="false"
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

        <!-- 加入业务单元 -->
        <el-form-item
          :label="t('permission.businessUnit')"
          required
        >
          <el-tree-select
            v-model="applyForm.businessUnitId"
            :data="applicableBusinessUnitTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            :placeholder="t('permission.selectBusinessUnit')"
            style="width: 100%;"
            filterable
            check-strictly
            :loading="loadingBusinessUnits"
            :disabled="!loadingBusinessUnits && applicableBusinessUnits.length === 0"
            :teleported="false"
            @change="onBusinessUnitChange"
          />
          <div
            v-if="!loadingBusinessUnits && applicableBusinessUnits.length === 0"
            class="form-hint"
          >
            {{ t('permission.noApplicableBusinessUnits') }}
          </div>
        </el-form-item>

        <el-form-item
          :label="t('permission.role')"
          required
        >
          <el-select
            v-model="applyForm.roleId"
            :placeholder="t('permission.selectRole')"
            style="width: 100%;"
            filterable
            :loading="loadingRoles"
            :disabled="!applyForm.businessUnitId || (!loadingRoles && eligibleRoles.length === 0)"
            :teleported="false"
          >
            <el-option
              v-for="role in eligibleRoles"
              :key="role.id"
              :label="role.name"
              :value="role.id"
            />
          </el-select>
          <div
            v-if="applyForm.businessUnitId && !loadingRoles && eligibleRoles.length === 0"
            class="form-hint"
          >
            {{ t('permission.noEligibleRoles') }}
          </div>
        </el-form-item>

        <el-form-item :label="t('permission.membershipType')" required>
          <div class="membership-row">
            <el-radio-group v-model="applyForm.membershipType">
              <el-radio value="MEMBER">
                {{ t('permission.member') }}
              </el-radio>
              <el-radio value="LEADER">
                {{ t('permission.leader') }}
              </el-radio>
            </el-radio-group>
            <PortalHelpLink
              path="/up-tasks-to-claim#leader"
              :aria-label="t('permission.applyLeaderGuideLinkAria')"
              test-id="apply-leader-guide-link"
            />
          </div>
          <div class="form-hint">
            {{ t('permission.membershipHint') }}
          </div>
        </el-form-item>

        <el-form-item
          :label="t('permission.reason')"
          required
        >
          <el-input
            v-model="applyForm.reason"
            type="textarea"
            :rows="3"
            :placeholder="t('permission.reasonPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitApply"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useApplyPermission } from '@/composables/permissions/useApplyPermission'
import { usePermissionFormatters } from '@/composables/permissions/usePermissionFormatters'
import PortalHelpLink from '@/components/PortalHelpLink.vue'

const emit = defineEmits<{
  success: []
}>()

const { t } = useI18n()
const { beneficiaryOptionLabel } = usePermissionFormatters(t)

const reload = () => emit('success')

const {
  applyDialogVisible,
  submitting,
  loadingBusinessUnits,
  loadingRoles,
  loadingBeneficiarySearch,
  beneficiaryOptions,
  applicableBusinessUnits,
  applicableBusinessUnitTree,
  eligibleRoles,
  applyForm,
  searchBeneficiaryUsers,
  showApplyDialog,
  onBusinessUnitChange,
  submitApply,
} = useApplyPermission(t, {
  loadPendingRequests: reload,
  loadHistoryRequests: reload,
})

defineExpose({ open: showApplyDialog })
</script>

<style lang="scss" scoped>
.apply-form {
  .form-hint {
    margin-top: 6px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.4;
  }

  .membership-row {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}
</style>
