<template>
    <el-dialog
      v-model="removePermissionDialogVisible"
      :title="t('permission.removePermissionDialogTitle')"
      width="720px"
      destroy-on-close
      class="remove-permission-dialog"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="remove-permission-alert"
      >
        {{ t('permission.removePermissionIntro') }}
      </el-alert>
      <el-form
        label-position="top"
        class="apply-form removal-form"
      >
        <el-form-item :label="t('permission.beneficiary')">
          <el-select
            v-model="removalBeneficiaryUserId"
            filterable
            remote
            clearable
            reserve-keyword
            :placeholder="t('permission.beneficiaryPlaceholder')"
            :remote-method="searchRemovalBeneficiaries"
            :loading="loadingRemovalBeneficiarySearch"
            style="width: 100%"
            :teleported="false"
          >
            <el-option
              v-for="u in removalBeneficiaryOptions"
              :key="u.userId"
              :label="beneficiaryOptionLabel(u)"
              :value="u.userId"
            />
          </el-select>
          <div class="form-hint">
            {{ t('permission.beneficiaryHint') }}
          </div>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            plain
            :loading="loadingRemovalOptions"
            @click="loadRemovalOptions"
          >
            {{ removalPayload ? t('permission.removalReload') : t('permission.removalLoadOptions') }}
          </el-button>
          <span
            v-if="selectedRemovalKeys.length"
            class="selected-count"
          >
            {{ t('permission.removalSelectedCount', { n: selectedRemovalKeys.length }) }}
          </span>
        </el-form-item>
      </el-form>

      <div
        v-loading="loadingRemovalOptions"
        class="removal-options-body"
      >
        <template v-if="removalPayload && !loadingRemovalOptions">
          <el-empty
            v-if="totalRemovableCount === 0"
            :description="t('permission.removalEmpty')"
          />
          <template v-else>
            <h4
              v-if="removalPayload.functionUnitGroups.length"
              class="removal-section-title"
            >
              {{ t('permission.removalFunctionUnitSection') }}
            </h4>
            <el-collapse
              v-if="removalPayload.functionUnitGroups.length"
              v-model="activeFuCollapseNames"
              class="fu-collapse"
            >
              <el-collapse-item
                v-for="g in removalPayload.functionUnitGroups"
                :key="g.functionUnitId"
                :name="g.functionUnitId"
              >
                <template #title>
                  <div class="fu-collapse-title">
                    <el-checkbox
                      :model-value="groupCheckState(g).checked"
                      :indeterminate="groupCheckState(g).indeterminate"
                      @change="(v: boolean | string | number) => toggleGroupAll(g, Boolean(v))"
                      @click.stop
                    />
                    <span class="fu-title-text">{{ g.functionUnitName || g.functionUnitId }}</span>
                    <el-tag
                      v-if="g.functionUnitCode"
                      size="small"
                      type="info"
                      class="fu-code-tag"
                    >
                      {{ t('permission.removalFunctionUnitCode') }}: {{ g.functionUnitCode }}
                    </el-tag>
                  </div>
                </template>
                <p class="fu-select-all-hint">
                  {{ t('permission.removalSelectAllInFu') }}
                </p>
                <div class="assignment-rows">
                  <el-checkbox
                    v-for="a in g.assignments"
                    :key="rowRemovalKey(a.businessUnitId, a.roleId)"
                    :model-value="selectedRemovalKeys.includes(rowRemovalKey(a.businessUnitId, a.roleId))"
                    class="assignment-check"
                    @change="(v: boolean | string | number) => toggleRemovalKey(rowRemovalKey(a.businessUnitId, a.roleId), Boolean(v))"
                  >
                    {{ removalRowLabel(a) }}
                  </el-checkbox>
                </div>
              </el-collapse-item>
            </el-collapse>

            <template v-if="removalPayload.otherAssignments.length">
              <h4 class="removal-section-title other-title">
                {{ t('permission.removalOtherSection') }}
              </h4>
              <p class="other-hint">
                {{ t('permission.removalOtherHint') }}
              </p>
              <div class="other-actions">
                <el-button
                  text
                  type="primary"
                  size="small"
                  @click="toggleOtherAll(true)"
                >
                  {{ t('common.all') }}
                </el-button>
                <el-button
                  text
                  type="info"
                  size="small"
                  @click="toggleOtherAll(false)"
                >
                  {{ t('common.clear') }}
                </el-button>
              </div>
              <div class="assignment-rows">
                <el-checkbox
                  v-for="a in removalPayload.otherAssignments"
                  :key="rowRemovalKey(a.businessUnitId, a.roleId)"
                  :model-value="selectedRemovalKeys.includes(rowRemovalKey(a.businessUnitId, a.roleId))"
                  class="assignment-check"
                  @change="(v: boolean | string | number) => toggleRemovalKey(rowRemovalKey(a.businessUnitId, a.roleId), Boolean(v))"
                >
                  {{ removalRowLabel(a) }}
                </el-checkbox>
              </div>
            </template>
          </template>
        </template>
      </div>

      <el-form
        label-position="top"
        class="apply-form"
      >
        <el-form-item
          :label="t('permission.reason')"
          required
        >
          <el-input
            v-model="removePermissionReason"
            type="textarea"
            :rows="3"
            :placeholder="t('permission.reasonPlaceholder')"
          />
        </el-form-item>
        <p class="batch-note">
          {{ t('permission.removalBatchNote') }}
        </p>
      </el-form>
      <template #footer>
        <el-button @click="removePermissionDialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="danger"
          :loading="submittingRemovalBatch"
          :disabled="selectedRemovalKeys.length === 0"
          @click="submitRemovalBatch"
        >
          {{ t('permission.removalSubmitSelected') }}
        </el-button>
      </template>
    </el-dialog>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRemovePermission } from '@/composables/permissions/useRemovePermission'
import { usePermissionFormatters } from '@/composables/permissions/usePermissionFormatters'

const emit = defineEmits<{
  success: []
  'my-bu-roles-changed': []
  'exit-bu-changed': []
}>()

const { t } = useI18n()
const {
  rowRemovalKey,
  removalRowLabel,
  beneficiaryOptionLabel,
} = usePermissionFormatters(t)

const reload = () => emit('success')

const {
  removePermissionDialogVisible,
  removalBeneficiaryUserId,
  removalBeneficiaryOptions,
  loadingRemovalBeneficiarySearch,
  removalPayload,
  loadingRemovalOptions,
  selectedRemovalKeys,
  activeFuCollapseNames,
  removePermissionReason,
  submittingRemovalBatch,
  totalRemovableCount,
  groupCheckState,
  toggleRemovalKey,
  toggleGroupAll,
  toggleOtherAll,
  searchRemovalBeneficiaries,
  openRemovePermissionDialog,
  loadRemovalOptions,
  submitRemovalBatch,
} = useRemovePermission(t, {
  rowRemovalKey,
  loadPendingRequests: reload,
  loadHistoryRequests: reload,
  loadMyBuRoles: () => emit('my-bu-roles-changed'),
  loadExitBuMemberships: () => emit('exit-bu-changed'),
})

defineExpose({ open: openRemovePermissionDialog })
</script>

<style lang="scss">
.remove-permission-dialog {
  .remove-permission-alert {
    margin-bottom: 14px;
  }

  .removal-form {
    margin-bottom: 8px;
  }

  .selected-count {
    margin-left: 12px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  .removal-options-body {
    min-height: 80px;
    margin-bottom: 12px;
  }

  .removal-section-title {
    margin: 0 0 8px;
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .other-title {
    margin-top: 16px;
  }

  .other-hint {
    margin: 0 0 8px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.4;
  }

  .other-actions {
    margin-bottom: 8px;
  }

  .fu-collapse.el-collapse {
    border: none;
  }

  .fu-collapse .el-collapse-item__header {
    height: auto;
    min-height: 48px;
    line-height: 1.4;
    padding-right: 8px;
  }

  .fu-collapse-title {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    width: 100%;
    padding: 4px 0;
  }

  .fu-title-text {
    font-weight: 500;
    color: var(--el-text-color-primary);
  }

  .fu-code-tag {
    font-weight: normal;
  }

  .fu-select-all-hint {
    margin: 0 0 8px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .assignment-rows {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 4px 0 8px;
  }

  .assignment-check.el-checkbox {
    margin-right: 0;
    align-items: flex-start;
    white-space: normal;
    height: auto;
  }

  .batch-note {
    margin: 0;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

</style>

<style lang="scss" scoped>
.apply-form {
  .form-hint {
    margin-top: 6px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.4;
  }
}
</style>
