<template>
  <div class="permissions-page page-stack">
    <div class="page-header">
      <h1>{{ t('permission.title') }}</h1>
      <div class="page-header-actions">
        <el-button
          type="primary"
          @click="applyDialogRef?.open()"
        >
          {{ t('permission.applyPermission') }}
        </el-button>
        <el-button
          type="danger"
          plain
          @click="removeDialogRef?.open()"
        >
          {{ t('permission.removePermission') }}
        </el-button>
      </div>
    </div>

    <PermissionMyBuRolesCard ref="myBuRolesRef" />

    <PermissionExitBuSection
      ref="exitBuRef"
      @requests-changed="reloadMyLists"
      @my-bu-roles-changed="() => myBuRolesRef?.reload()"
    />

    <!-- 申请与审批（合并原 My requests + Approvals） -->
    <div class="portal-card request-lists-card">
      <h2 class="section-title">
        {{ t('permission.sectionRequestsAndApprovals') }}
      </h2>
      <el-alert
        v-if="showPendingApprovalsBanner"
        class="pending-approvals-banner"
        type="warning"
        :closable="false"
        show-icon
        :title="t('permission.pendingApprovalsBanner', { count: approvalPendingCount })"
      />
      <el-select
        v-model="primaryWorkTab"
        class="work-domain-select"
      >
        <el-option
          :label="t('permission.sectionMyRequests')"
          value="myRequests"
        />
        <el-option
          v-if="isApprover"
          :label="t('permission.sectionApprovals')"
          value="approvals"
        />
      </el-select>

      <div v-if="!isApprover || primaryWorkTab === 'myRequests'">
        <el-tabs
          v-model="myRequestTab"
          class="list-tabs"
        >
          <el-tab-pane name="inProgress">
            <template #label>
              <span>{{ t('permission.tabInProgress') }}</span>
              <el-badge
                v-if="pendingCount > 0"
                :value="pendingCount"
                class="tab-badge"
              />
            </template>
            <PermissionRequestSharedList
              ref="myPendingListRef"
              scope="MY_PENDING"
              storage-key="portal-list-layout:permission-my-pending"
              :empty-text="t('permission.noPendingRequests')"
              action-mode="cancel"
              @total="onMyPendingTotal"
              @cancel="cancelRequest"
            />
          </el-tab-pane>

          <el-tab-pane
            :label="t('permission.tabCompleted')"
            name="completed"
          >
            <PermissionRequestSharedList
              ref="myCompletedListRef"
              scope="MY_COMPLETED"
              storage-key="portal-list-layout:permission-my-completed"
              :empty-text="t('permission.noRequests')"
              action-mode="none"
            />
          </el-tab-pane>
        </el-tabs>
      </div>

      <div v-if="isApprover && primaryWorkTab === 'approvals'">
        <el-tabs
          v-model="approvalTab"
          class="list-tabs"
        >
          <el-tab-pane name="pendingApproval">
            <template #label>
              <span>{{ t('permission.tabPendingApproval') }}</span>
              <el-badge
                v-if="approvalPendingCount > 0"
                :value="approvalPendingCount"
                class="tab-badge"
              />
            </template>
            <PermissionRequestSharedList
              ref="approvalPendingListRef"
              scope="APPROVALS_PENDING"
              storage-key="portal-list-layout:permission-approvals-pending"
              :empty-text="t('approval.noPendingApprovals')"
              action-mode="approve"
              :enabled="isApprover"
              @total="onApprovalPendingTotal"
              @approve="showApproveDialog"
              @reject="showRejectDialog"
            />
          </el-tab-pane>

          <el-tab-pane
            :label="t('permission.tabApprovalHistory')"
            name="approvalHistory"
          >
            <PermissionRequestSharedList
              ref="approvalHistoryListRef"
              scope="APPROVALS_HISTORY"
              storage-key="portal-list-layout:permission-approvals-history"
              :empty-text="t('approval.noApprovalHistory')"
              action-mode="none"
              :enabled="isApprover"
            />
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- 批准 / 拒绝（审批人） -->
    <el-dialog
      v-model="approveDialogVisible"
      :title="t('approval.approveTitle')"
      width="500px"
    >
      <div class="approval-dialog-info">
        <p><strong>{{ t('approval.applicant') }}:</strong> {{ getApplicantDisplay(currentApproverRequest) }}</p>
        <p><strong>{{ t('permission.requestType') }}:</strong> {{ getRequestTypeLabel(currentApproverRequest?.requestType) }}</p>
        <p><strong>{{ t('permission.requestTarget') }}:</strong> {{ getTargetName(currentApproverRequest) }}</p>
        <p><strong>{{ t('permission.reason') }}:</strong> {{ currentApproverRequest?.reason }}</p>
      </div>
      <el-form-item :label="t('approval.comment')">
        <el-input
          v-model="approveComment"
          type="textarea"
          :rows="3"
          :placeholder="t('approval.commentPlaceholder')"
        />
      </el-form-item>
      <template #footer>
        <el-button @click="approveDialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="success"
          :loading="submittingApproval"
          @click="handleApprove"
        >
          {{ t('approval.approve') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="rejectDialogVisible"
      :title="t('approval.rejectTitle')"
      width="500px"
    >
      <div class="approval-dialog-info">
        <p><strong>{{ t('approval.applicant') }}:</strong> {{ getApplicantDisplay(currentApproverRequest) }}</p>
        <p><strong>{{ t('permission.requestType') }}:</strong> {{ getRequestTypeLabel(currentApproverRequest?.requestType) }}</p>
        <p><strong>{{ t('permission.requestTarget') }}:</strong> {{ getTargetName(currentApproverRequest) }}</p>
        <p><strong>{{ t('permission.reason') }}:</strong> {{ currentApproverRequest?.reason }}</p>
      </div>
      <el-form-item
        :label="t('approval.rejectReason')"
        required
      >
        <el-input
          v-model="rejectComment"
          type="textarea"
          :rows="3"
          :placeholder="t('approval.rejectReasonPlaceholder')"
        />
      </el-form-item>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="danger"
          :loading="submittingApproval"
          @click="handleReject"
        >
          {{ t('approval.reject') }}
        </el-button>
      </template>
    </el-dialog>

    <PermissionApplyDialog
      ref="applyDialogRef"
      @success="reloadMyLists"
    />
    <PermissionRemoveDialog
      ref="removeDialogRef"
      @success="reloadMyLists"
      @my-bu-roles-changed="() => myBuRolesRef?.reload()"
      @exit-bu-changed="() => exitBuRef?.reload()"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePendingApprovalStore } from '@/stores/pendingApproval'
import PermissionRequestSharedList from '@/components/permissions/PermissionRequestSharedList.vue'
import PermissionMyBuRolesCard from '@/components/permissions/PermissionMyBuRolesCard.vue'
import PermissionExitBuSection from '@/components/permissions/PermissionExitBuSection.vue'
import PermissionApplyDialog from '@/components/permissions/PermissionApplyDialog.vue'
import PermissionRemoveDialog from '@/components/permissions/PermissionRemoveDialog.vue'
import { usePermissionFormatters } from '@/composables/permissions/usePermissionFormatters'
import { useMyRequests } from '@/composables/permissions/useMyRequests'
import { useApprovals } from '@/composables/permissions/useApprovals'
import {
  resolvePrimaryWorkTab,
  shouldShowPendingApprovalsBanner,
  type PrimaryWorkTab,
} from '@/utils/permissionWorkTabs'

const { t } = useI18n()
const pendingApprovalStore = usePendingApprovalStore()
const primaryWorkTab = ref<PrimaryWorkTab>('myRequests')

type SharedListExpose = { reload: () => void | Promise<void> }
type OpenExpose = { open: () => void }
type ReloadExpose = { reload: () => void | Promise<void> }

const myPendingListRef = ref<SharedListExpose | null>(null)
const myCompletedListRef = ref<SharedListExpose | null>(null)
const approvalPendingListRef = ref<SharedListExpose | null>(null)
const approvalHistoryListRef = ref<SharedListExpose | null>(null)
const applyDialogRef = ref<OpenExpose | null>(null)
const removeDialogRef = ref<OpenExpose | null>(null)
const myBuRolesRef = ref<ReloadExpose | null>(null)
const exitBuRef = ref<ReloadExpose | null>(null)

const pendingCount = ref(0)
const approvalPendingCount = ref(0)

function reloadMyLists() {
  void myPendingListRef.value?.reload()
  void myCompletedListRef.value?.reload()
}

function reloadApprovalLists() {
  void approvalPendingListRef.value?.reload()
  void approvalHistoryListRef.value?.reload()
}

const {
  getApplicantDisplay,
  getRequestTypeLabel,
  getTargetName,
} = usePermissionFormatters(t)

const { myRequestTab, cancelRequest } = useMyRequests(t, { reloadMyLists })

const {
  approvalTab,
  isApprover,
  approveDialogVisible,
  rejectDialogVisible,
  currentApproverRequest,
  approveComment,
  rejectComment,
  submittingApproval,
  checkApproverStatus,
  showApproveDialog,
  showRejectDialog,
  handleApprove,
  handleReject,
} = useApprovals(t, {
  reloadMyLists,
  reloadApprovalLists,
  fetchPendingCount: () => pendingApprovalStore.fetchPendingCount(),
})

const showPendingApprovalsBanner = computed(() =>
  shouldShowPendingApprovalsBanner({
    isApprover: isApprover.value,
    approvalPendingCount: approvalPendingCount.value,
  }),
)

function onMyPendingTotal(n: number) {
  pendingCount.value = n
}

function onApprovalPendingTotal(n: number) {
  approvalPendingCount.value = n
}

onMounted(async () => {
  await checkApproverStatus()
  await pendingApprovalStore.fetchPendingCount()
  if (isApprover.value && pendingApprovalStore.count > 0) {
    approvalPendingCount.value = pendingApprovalStore.count
  }
  primaryWorkTab.value = resolvePrimaryWorkTab({
    isApprover: isApprover.value,
    approvalPendingCount: approvalPendingCount.value,
  })
})
</script>

<style lang="scss" scoped>
.permissions-page {
  .page-header {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    margin-bottom: 20px;

    h1 {
      font-size: 24px;
      font-weight: 500;
      color: var(--text-primary);
      margin: 0;
    }

    .page-header-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }
  }

  .tab-badge {
    margin-left: 6px;
  }

  .request-lists-card {
    margin-bottom: 16px;

    .section-title {
      margin: 0 0 12px;
      font-size: 16px;
      font-weight: 600;
    }
  }

  .pending-approvals-banner {
    margin-bottom: 12px;
  }

  .work-domain-select {
    width: 220px;
    margin-bottom: 12px;
  }

  .list-tabs {
    :deep(.el-tabs__header) {
      margin-bottom: 12px;
    }
  }
}
</style>

<style lang="scss">
.approval-dialog-info {
  margin-bottom: 16px;
  padding: 12px 14px;
  background: var(--el-fill-color-light);
  border-radius: 8px;

  p {
    margin: 6px 0;
    font-size: 13px;
    line-height: 1.5;
  }

  strong {
    color: var(--el-text-color-secondary);
    margin-right: 8px;
  }
}
</style>
