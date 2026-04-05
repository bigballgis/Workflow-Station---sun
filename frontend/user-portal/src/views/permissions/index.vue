<template>
  <div class="permissions-page">
    <div class="page-header">
      <h1>{{ t('permission.title') }}</h1>
      <div class="page-header-actions">
        <el-button type="primary" @click="showApplyDialog">{{ t('permission.applyPermission') }}</el-button>
        <el-button type="danger" plain @click="openRemovePermissionDialog">{{ t('permission.removePermission') }}</el-button>
      </div>
    </div>

    <div class="portal-card my-bu-roles-card">
      <h2 class="section-title">{{ t('permission.myBuRoles') }}</h2>
      <el-empty
        v-if="myBuRoles.length === 0 && !loadingMyBuRoles"
        :description="t('permission.noMyBuRoles')"
      />
      <el-table v-else :data="myBuRoles" stripe v-loading="loadingMyBuRoles">
        <el-table-column :label="t('permission.businessUnit')" min-width="160">
          <template #default="{ row }">
            {{ row.businessUnitName || row.businessUnitId || '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('permission.role')" min-width="140">
          <template #default="{ row }">
            {{ row.roleName || row.roleId || '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('permission.assignedAt')" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.assignedAt || row.createdAt) }}
          </template>
        </el-table-column>
      </el-table>
      <p class="table-foot-hint">{{ t('permission.requestRemoveBuRoleHint') }}</p>
    </div>

    <div class="portal-card exit-bu-card">
      <h2 class="section-title">{{ t('exitRole.title') }}</h2>
      <p class="section-sub">{{ t('exitRole.subtitle') }}</p>
      <el-alert type="info" show-icon :closable="false" class="info-alert">
        {{ t('exitRole.portalNoVirtualGroup') }}
      </el-alert>
      <el-empty
        v-if="!loadingExitBu && exitBuRows.length === 0"
        :description="t('exitRole.noMemberships')"
      />
      <el-table v-else :data="exitBuRows" stripe v-loading="loadingExitBu">
        <el-table-column prop="businessUnitName" :label="t('exitRole.businessUnit')" min-width="200" />
        <el-table-column prop="joinedAt" :label="t('exitRole.joinTime')" width="180">
          <template #default="{ row }">{{ formatDateTime(row.joinedAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="openExitBuDialog(row)">
              {{ t('exitRole.requestExitBu') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 我的申请 -->
    <div class="portal-card request-lists-card">
      <h2 class="section-title">{{ t('permission.sectionMyRequests') }}</h2>
      <el-tabs v-model="myRequestTab" class="list-tabs">
        <el-tab-pane name="inProgress">
          <template #label>
            <span>{{ t('permission.tabInProgress') }}</span>
            <el-badge v-if="pendingCount > 0" :value="pendingCount" class="tab-badge" />
          </template>
          <el-empty v-if="pendingList.length === 0 && !loadingPending" :description="t('permission.noPendingRequests')" />
          <el-table v-else :data="pendingList" stripe v-loading="loadingPending">
            <el-table-column prop="requestType" :label="t('permission.requestType')" width="160">
              <template #default="{ row }">
                <el-tag :type="getRequestTypeTag(row.requestType)" size="small">
                  {{ getRequestTypeLabel(row.requestType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('permission.requestTarget')" min-width="150">
              <template #default="{ row }">
                {{ getTargetName(row) }}
              </template>
            </el-table-column>
            <el-table-column :label="t('permission.beneficiaryColumn')" width="130" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.applicantUsername || row.applicantId || '-' }}
              </template>
            </el-table-column>
            <el-table-column :label="t('permission.submittedByColumn')" width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.submittedByUserId && row.submittedByUserId !== row.applicantId">
                  {{ row.submittedByUsername || row.submittedByUserId }}
                  <el-tag size="small" type="info" class="proxy-tag">{{ t('permission.proxyBadge') }}</el-tag>
                </span>
                <span v-else>{{ t('permission.selfBeneficiary') }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="reason" :label="t('permission.reason')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="createdAt" :label="t('permission.applyTime')" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="150" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="canCancelAsBeneficiary(row)"
                  type="danger"
                  size="small"
                  text
                  @click="cancelRequest(row)"
                >
                  {{ t('permission.cancelRequest') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="t('permission.tabCompleted')" name="completed">
          <el-empty v-if="historyList.length === 0 && !loadingHistory" :description="t('permission.noRequests')" />
          <el-table v-else :data="historyList" stripe v-loading="loadingHistory">
            <el-table-column prop="requestType" :label="t('permission.requestType')" width="140">
              <template #default="{ row }">
                <el-tag :type="getRequestTypeTag(row.requestType)" size="small">
                  {{ getRequestTypeLabel(row.requestType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('permission.requestTarget')" min-width="180">
              <template #default="{ row }">
                {{ getTargetName(row) }}
              </template>
            </el-table-column>
            <el-table-column :label="t('permission.beneficiaryColumn')" width="120" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.applicantUsername || row.applicantId || '-' }}
              </template>
            </el-table-column>
            <el-table-column :label="t('permission.submittedByColumn')" width="110" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.submittedByUserId && row.submittedByUserId !== row.applicantId">
                  {{ row.submittedByUsername || row.submittedByUserId }}
                </span>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column prop="reason" :label="t('permission.reason')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="status" :label="t('permission.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">
                  {{ getStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="approverComment" :label="t('approval.comment')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="createdAt" :label="t('permission.applyTime')" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" :label="t('permission.approvedAt')" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.updatedAt) }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 审批（仅审批人可见） -->
    <div v-if="isApprover" class="portal-card request-lists-card approval-lists-card">
      <h2 class="section-title">{{ t('permission.sectionApprovals') }}</h2>
      <el-tabs v-model="approvalTab" class="list-tabs" @tab-change="onApprovalTabChange">
        <el-tab-pane name="pendingApproval">
          <template #label>
            <span>{{ t('permission.tabPendingApproval') }}</span>
            <el-badge v-if="approvalPendingCount > 0" :value="approvalPendingCount" class="tab-badge" />
          </template>
          <el-empty
            v-if="approverPendingList.length === 0 && !loadingApproverPending"
            :description="t('approval.noPendingApprovals')"
          />
          <el-table v-else :data="approverPendingList" stripe v-loading="loadingApproverPending">
            <el-table-column prop="applicantId" :label="t('permission.beneficiaryColumn')" width="150">
              <template #default="{ row }">
                {{ getApplicantDisplay(row) }}
              </template>
            </el-table-column>
            <el-table-column :label="t('permission.submittedByColumn')" width="130" show-overflow-tooltip>
              <template #default="{ row }">
                {{ getSubmitterDisplay(row) }}
              </template>
            </el-table-column>
            <el-table-column prop="requestType" :label="t('permission.requestType')" width="140">
              <template #default="{ row }">
                <el-tag :type="getRequestTypeTag(row.requestType)" size="small">
                  {{ getRequestTypeLabel(row.requestType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('permission.requestTarget')" min-width="180">
              <template #default="{ row }">
                {{ getTargetName(row) }}
              </template>
            </el-table-column>
            <el-table-column prop="reason" :label="t('permission.reason')" min-width="200" show-overflow-tooltip />
            <el-table-column prop="createdAt" :label="t('permission.applyTime')" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="180" fixed="right">
              <template #default="{ row }">
                <el-button type="success" size="small" @click="showApproveDialog(row)">
                  {{ t('approval.approve') }}
                </el-button>
                <el-button type="danger" size="small" @click="showRejectDialog(row)">
                  {{ t('approval.reject') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="t('permission.tabApprovalHistory')" name="approvalHistory">
          <el-empty
            v-if="approverHistoryList.length === 0 && !loadingApproverHistory"
            :description="t('approval.noApprovalHistory')"
          />
          <el-table v-else :data="approverHistoryList" stripe v-loading="loadingApproverHistory">
            <el-table-column prop="applicantId" :label="t('permission.beneficiaryColumn')" width="150">
              <template #default="{ row }">
                {{ getApplicantDisplay(row) }}
              </template>
            </el-table-column>
            <el-table-column :label="t('permission.submittedByColumn')" width="130" show-overflow-tooltip>
              <template #default="{ row }">
                {{ getSubmitterDisplay(row) }}
              </template>
            </el-table-column>
            <el-table-column prop="requestType" :label="t('permission.requestType')" width="140">
              <template #default="{ row }">
                <el-tag :type="getRequestTypeTag(row.requestType)" size="small">
                  {{ getRequestTypeLabel(row.requestType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('permission.requestTarget')" min-width="180">
              <template #default="{ row }">
                {{ getTargetName(row) }}
              </template>
            </el-table-column>
            <el-table-column prop="status" :label="t('permission.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">
                  {{ getStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="approverComment" :label="t('approval.comment')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="approvedAt" :label="t('approval.processedAt')" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.approvedAt || row.updatedAt) }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 批准 / 拒绝（审批人） -->
    <el-dialog v-model="approveDialogVisible" :title="t('approval.approveTitle')" width="500px">
      <div class="approval-dialog-info">
        <p><strong>{{ t('approval.applicant') }}:</strong> {{ getApplicantDisplay(currentApproverRequest) }}</p>
        <p><strong>{{ t('permission.requestType') }}:</strong> {{ getRequestTypeLabel(currentApproverRequest?.requestType) }}</p>
        <p><strong>{{ t('permission.requestTarget') }}:</strong> {{ getTargetName(currentApproverRequest) }}</p>
        <p><strong>{{ t('permission.reason') }}:</strong> {{ currentApproverRequest?.reason }}</p>
      </div>
      <el-form-item :label="t('approval.comment')">
        <el-input v-model="approveComment" type="textarea" :rows="3" :placeholder="t('approval.commentPlaceholder')" />
      </el-form-item>
      <template #footer>
        <el-button @click="approveDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="success" :loading="submittingApproval" @click="handleApprove">
          {{ t('approval.approve') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectDialogVisible" :title="t('approval.rejectTitle')" width="500px">
      <div class="approval-dialog-info">
        <p><strong>{{ t('approval.applicant') }}:</strong> {{ getApplicantDisplay(currentApproverRequest) }}</p>
        <p><strong>{{ t('permission.requestType') }}:</strong> {{ getRequestTypeLabel(currentApproverRequest?.requestType) }}</p>
        <p><strong>{{ t('permission.requestTarget') }}:</strong> {{ getTargetName(currentApproverRequest) }}</p>
        <p><strong>{{ t('permission.reason') }}:</strong> {{ currentApproverRequest?.reason }}</p>
      </div>
      <el-form-item :label="t('approval.rejectReason')" required>
        <el-input v-model="rejectComment" type="textarea" :rows="3" :placeholder="t('approval.rejectReasonPlaceholder')" />
      </el-form-item>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="danger" :loading="submittingApproval" @click="handleReject">
          {{ t('approval.reject') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 申请权限对话框 -->
    <el-dialog v-model="applyDialogVisible" :title="t('permission.applyPermission')" width="600px">
      <el-form :model="applyForm" label-width="120px" label-position="left" class="apply-form">
        <!-- 申请类型选择 -->
        <el-form-item :label="t('permission.applyType')">
          <el-tag type="primary" size="large">{{ t('permission.joinBusinessUnit') }}</el-tag>
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
          <div class="form-hint">{{ t('permission.beneficiaryHint') }}</div>
        </el-form-item>

        <!-- 加入业务单元 -->
        <el-form-item :label="t('permission.businessUnit')" required>
          <el-select 
            v-model="applyForm.businessUnitId" 
            :placeholder="t('permission.selectBusinessUnit')" 
            style="width: 100%;" 
            filterable 
            :loading="loadingBusinessUnits"
            :disabled="!loadingBusinessUnits && applicableBusinessUnits.length === 0"
            @change="onBusinessUnitChange"
            :teleported="false"
          >
            <el-option
              v-for="bu in applicableBusinessUnits"
              :key="bu.id"
              :label="bu.name"
              :value="bu.id"
            />
          </el-select>
          <div v-if="!loadingBusinessUnits && applicableBusinessUnits.length === 0" class="form-hint">
            {{ t('permission.noApplicableBusinessUnits') }}
          </div>
        </el-form-item>

        <el-form-item :label="t('permission.role')" required>
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
          <div v-if="applyForm.businessUnitId && !loadingRoles && eligibleRoles.length === 0" class="form-hint">
            {{ t('permission.noEligibleRoles') }}
          </div>
        </el-form-item>

        <el-form-item :label="t('permission.reason')" required>
          <el-input v-model="applyForm.reason" type="textarea" :rows="3" :placeholder="t('permission.reasonPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitApply" :loading="submitting">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 按功能单元多选移除 BU 角色 -->
    <el-dialog
      v-model="removePermissionDialogVisible"
      :title="t('permission.removePermissionDialogTitle')"
      width="720px"
      destroy-on-close
      class="remove-permission-dialog"
    >
      <el-alert type="info" :closable="false" show-icon class="remove-permission-alert">
        {{ t('permission.removePermissionIntro') }}
      </el-alert>
      <el-form label-position="top" class="apply-form removal-form">
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
          <div class="form-hint">{{ t('permission.beneficiaryHint') }}</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain :loading="loadingRemovalOptions" @click="loadRemovalOptions">
            {{ removalPayload ? t('permission.removalReload') : t('permission.removalLoadOptions') }}
          </el-button>
          <span v-if="selectedRemovalKeys.length" class="selected-count">
            {{ t('permission.removalSelectedCount', { n: selectedRemovalKeys.length }) }}
          </span>
        </el-form-item>
      </el-form>

      <div v-loading="loadingRemovalOptions" class="removal-options-body">
        <template v-if="removalPayload && !loadingRemovalOptions">
          <el-empty v-if="totalRemovableCount === 0" :description="t('permission.removalEmpty')" />
          <template v-else>
            <h4 v-if="removalPayload.functionUnitGroups.length" class="removal-section-title">
              {{ t('permission.removalFunctionUnitSection') }}
            </h4>
            <el-collapse v-if="removalPayload.functionUnitGroups.length" v-model="activeFuCollapseNames" class="fu-collapse">
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
                    <el-tag v-if="g.functionUnitCode" size="small" type="info" class="fu-code-tag">
                      {{ t('permission.removalFunctionUnitCode') }}: {{ g.functionUnitCode }}
                    </el-tag>
                  </div>
                </template>
                <p class="fu-select-all-hint">{{ t('permission.removalSelectAllInFu') }}</p>
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
              <h4 class="removal-section-title other-title">{{ t('permission.removalOtherSection') }}</h4>
              <p class="other-hint">{{ t('permission.removalOtherHint') }}</p>
              <div class="other-actions">
                <el-button text type="primary" size="small" @click="toggleOtherAll(true)">{{ t('common.all') }}</el-button>
                <el-button text type="info" size="small" @click="toggleOtherAll(false)">{{ t('common.clear') }}</el-button>
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

      <el-form label-position="top" class="apply-form">
        <el-form-item :label="t('permission.reason')" required>
          <el-input
            v-model="removePermissionReason"
            type="textarea"
            :rows="3"
            :placeholder="t('permission.reasonPlaceholder')"
          />
        </el-form-item>
        <p class="batch-note">{{ t('permission.removalBatchNote') }}</p>
      </el-form>
      <template #footer>
        <el-button @click="removePermissionDialogVisible = false">{{ t('common.cancel') }}</el-button>
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

    <!-- 申请退出业务单元 -->
    <el-dialog v-model="exitBuDialogVisible" :title="t('exitRole.requestExitBuTitle')" width="520px" destroy-on-close>
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
          <div class="form-hint">{{ t('permission.beneficiaryHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('permission.reason')" required>
          <el-input v-model="exitBuForm.reason" type="textarea" :rows="3" :placeholder="t('permission.reasonPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exitBuDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="exitBuSubmitting" @click="submitExitBu">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  permissionApi,
  type BusinessUnit,
  type FunctionUnitRemovalGroup,
  type PermissionRequestRecord,
  type RemovalAssignmentRow,
  type RemovalOptionsByFunctionUnitPayload,
  type RoleInfo,
  type UserBusinessUnitRole
} from '@/api/permission'
import { getStoredUser } from '@/api/auth'
import { usePendingApprovalStore } from '@/stores/pendingApproval'

const { t } = useI18n()
const pendingApprovalStore = usePendingApprovalStore()

const myRequestTab = ref('inProgress')
const approvalTab = ref('pendingApproval')
const isApprover = ref(false)
const approverPendingList = ref<PermissionRequestRecord[]>([])
const approverHistoryList = ref<PermissionRequestRecord[]>([])
const loadingApproverPending = ref(false)
const loadingApproverHistory = ref(false)
const approveDialogVisible = ref(false)
const rejectDialogVisible = ref(false)
const currentApproverRequest = ref<PermissionRequestRecord | null>(null)
const approveComment = ref('')
const rejectComment = ref('')
const submittingApproval = ref(false)

const applyDialogVisible = ref(false)
const submitting = ref(false)
const loadingPending = ref(false)
const loadingHistory = ref(false)
const loadingBusinessUnits = ref(false)
const loadingRoles = ref(false)
const loadingBeneficiarySearch = ref(false)
const beneficiaryOptions = ref<{ userId: string; username: string; displayName?: string }[]>([])
const loadingMyBuRoles = ref(false)

/** 移除权限对话框 */
const removePermissionDialogVisible = ref(false)
const removalBeneficiaryUserId = ref('')
const removalBeneficiaryOptions = ref<{ userId: string; username: string; displayName?: string }[]>([])
const loadingRemovalBeneficiarySearch = ref(false)
const removalPayload = ref<RemovalOptionsByFunctionUnitPayload | null>(null)
const loadingRemovalOptions = ref(false)
const selectedRemovalKeys = ref<string[]>([])
const activeFuCollapseNames = ref<string[]>([])
const removePermissionReason = ref('')
const submittingRemovalBatch = ref(false)

/** 退出业务单元 */
const loadingExitBu = ref(false)
const exitBuRows = ref<{ businessUnitId: string; businessUnitName: string; joinedAt?: string }[]>([])
const exitBuDialogVisible = ref(false)
const exitBuSubmitting = ref(false)
const loadingExitBuBeneficiarySearch = ref(false)
const exitBuBeneficiaryOptions = ref<{ userId: string; username: string; displayName?: string }[]>([])
const exitBuForm = reactive({
  businessUnitId: '',
  businessUnitName: '',
  beneficiaryUserId: '' as string,
  reason: ''
})

// 数据
const myBuRoles = ref<UserBusinessUnitRole[]>([])
const pendingList = ref<PermissionRequestRecord[]>([])
const historyList = ref<PermissionRequestRecord[]>([])
const applicableBusinessUnits = ref<BusinessUnit[]>([])
const eligibleRoles = ref<RoleInfo[]>([])

// 待处理数量
const pendingCount = computed(() => pendingList.value.length)
const approvalPendingCount = computed(() => approverPendingList.value.length)

const totalRemovableCount = computed(() => {
  const p = removalPayload.value
  if (!p) return 0
  const inFu = p.functionUnitGroups.reduce((n, g) => n + g.assignments.length, 0)
  return inFu + p.otherAssignments.length
})

// 申请表单
const applyForm = reactive({
  beneficiaryUserId: '' as string,
  businessUnitId: '',
  roleId: '',
  reason: ''
})

const checkApproverStatus = async () => {
  try {
    const res = (await permissionApi.isApprover()) as { data?: { isApprover?: boolean }; isApprover?: boolean }
    isApprover.value = res?.data?.isApprover ?? res?.isApprover ?? false
  } catch (e) {
    console.error('Failed to check approver status:', e)
    isApprover.value = false
  }
}

const loadApproverPending = async () => {
  if (!isApprover.value) return
  loadingApproverPending.value = true
  try {
    const res = (await permissionApi.getPendingApprovals({ page: 0, size: 100 })) as any
    if (res?.data?.content) {
      approverPendingList.value = res.data.content
    } else if (res?.content) {
      approverPendingList.value = res.content
    } else if (Array.isArray(res)) {
      approverPendingList.value = res
    } else {
      approverPendingList.value = []
    }
  } catch (e) {
    console.error('Failed to load pending approvals:', e)
    approverPendingList.value = []
  } finally {
    loadingApproverPending.value = false
  }
}

const loadApproverHistory = async () => {
  if (!isApprover.value) return
  loadingApproverHistory.value = true
  try {
    const res = (await permissionApi.getApprovalHistory({ page: 0, size: 100 })) as any
    if (res?.data?.content) {
      approverHistoryList.value = res.data.content
    } else if (res?.content) {
      approverHistoryList.value = res.content
    } else if (Array.isArray(res)) {
      approverHistoryList.value = res
    } else {
      approverHistoryList.value = []
    }
  } catch (e) {
    console.error('Failed to load approval history:', e)
    approverHistoryList.value = []
  } finally {
    loadingApproverHistory.value = false
  }
}

const onApprovalTabChange = (tab: string | number) => {
  if (String(tab) === 'approvalHistory') {
    loadApproverHistory()
  }
}

const getApplicantDisplay = (row: PermissionRequestRecord | null | undefined) => {
  if (!row) return '-'
  return row.applicantName || row.applicantUsername || row.applicantId || '-'
}

const getSubmitterDisplay = (row: PermissionRequestRecord | null | undefined) => {
  if (!row?.submittedByUserId) return '—'
  if (row.submittedByUserId === row.applicantId) return t('permission.selfBeneficiary')
  return row.submittedByUsername || row.submittedByUserId
}

const showApproveDialog = (row: PermissionRequestRecord) => {
  currentApproverRequest.value = row
  approveComment.value = ''
  approveDialogVisible.value = true
}

const showRejectDialog = (row: PermissionRequestRecord) => {
  currentApproverRequest.value = row
  rejectComment.value = ''
  rejectDialogVisible.value = true
}

const handleApprove = async () => {
  if (!currentApproverRequest.value) return
  submittingApproval.value = true
  try {
    await permissionApi.approveRequest(currentApproverRequest.value.id, approveComment.value || undefined)
    ElMessage.success(t('approval.approveSuccess'))
    approveDialogVisible.value = false
    await loadApproverPending()
    await pendingApprovalStore.fetchPendingCount()
    approverHistoryList.value = []
    loadPendingRequests()
    loadHistoryRequests()
  } catch (e: any) {
    const msg = e.response?.data?.message || e.message || t('approval.approveFailed')
    ElMessage.error(msg)
  } finally {
    submittingApproval.value = false
  }
}

const handleReject = async () => {
  if (!currentApproverRequest.value) return
  if (!rejectComment.value.trim()) {
    ElMessage.warning(t('approval.rejectReasonRequired'))
    return
  }
  submittingApproval.value = true
  try {
    await permissionApi.rejectRequest(currentApproverRequest.value.id, rejectComment.value)
    ElMessage.success(t('approval.rejectSuccess'))
    rejectDialogVisible.value = false
    await loadApproverPending()
    await pendingApprovalStore.fetchPendingCount()
    approverHistoryList.value = []
    loadPendingRequests()
    loadHistoryRequests()
  } catch (e: any) {
    const msg = e.response?.data?.message || e.message || t('approval.rejectFailed')
    ElMessage.error(msg)
  } finally {
    submittingApproval.value = false
  }
}

// 加载待处理申请
const loadPendingRequests = async () => {
  loadingPending.value = true
  try {
    const res = await permissionApi.getRequestHistory({ status: 'PENDING', page: 0, size: 100 }) as any
    if (res?.data?.content) {
      pendingList.value = res.data.content
    } else if (res?.content) {
      pendingList.value = res.content
    } else if (Array.isArray(res)) {
      pendingList.value = res
    } else {
      pendingList.value = []
    }
  } catch (e) {
    console.error('Failed to load pending requests:', e)
    pendingList.value = []
  } finally {
    loadingPending.value = false
  }
}

// 加载历史记录（已批准和已拒绝）
const loadHistoryRequests = async () => {
  loadingHistory.value = true
  try {
    const res = await permissionApi.getRequestHistory({ page: 0, size: 50 }) as any
    let allRequests: any[] = []
    if (res?.data?.content) {
      allRequests = res.data.content
    } else if (res?.content) {
      allRequests = res.content
    } else if (Array.isArray(res)) {
      allRequests = res
    }
    // 过滤出已完成的申请（APPROVED, REJECTED, CANCELLED）
    historyList.value = allRequests.filter(
      (r: any) => r.status !== 'PENDING'
    )
  } catch (e) {
    console.error('Failed to load history requests:', e)
    historyList.value = []
  } finally {
    loadingHistory.value = false
  }
}

const loadMyBuRoles = async () => {
  loadingMyBuRoles.value = true
  try {
    const res = (await permissionApi.getMyMemberships()) as any
    const payload = res?.data ?? res
    const raw = payload?.businessUnitRoles
    if (Array.isArray(raw)) {
      myBuRoles.value = raw as UserBusinessUnitRole[]
    } else {
      myBuRoles.value = []
    }
  } catch (e) {
    console.error('Failed to load my BU roles:', e)
    myBuRoles.value = []
  } finally {
    loadingMyBuRoles.value = false
  }
}

const rowRemovalKey = (businessUnitId: string, roleId: string) => `${businessUnitId}::${roleId}`

const removalRowLabel = (a: RemovalAssignmentRow) =>
  t('permission.removalRowLabel', {
    bu: a.businessUnitName || a.businessUnitId,
    role: a.roleName || a.roleId
  })

const groupCheckState = (group: FunctionUnitRemovalGroup) => {
  const keys = group.assignments.map((x) => rowRemovalKey(x.businessUnitId, x.roleId))
  const n = keys.filter((k) => selectedRemovalKeys.value.includes(k)).length
  return {
    checked: n === keys.length && n > 0,
    indeterminate: n > 0 && n < keys.length
  }
}

const toggleRemovalKey = (key: string, on: boolean) => {
  const s = new Set(selectedRemovalKeys.value)
  if (on) s.add(key)
  else s.delete(key)
  selectedRemovalKeys.value = [...s]
}

const toggleGroupAll = (group: FunctionUnitRemovalGroup, checked: boolean) => {
  const s = new Set(selectedRemovalKeys.value)
  for (const a of group.assignments) {
    const k = rowRemovalKey(a.businessUnitId, a.roleId)
    if (checked) s.add(k)
    else s.delete(k)
  }
  selectedRemovalKeys.value = [...s]
}

const toggleOtherAll = (checked: boolean) => {
  const p = removalPayload.value
  if (!p) return
  const s = new Set(selectedRemovalKeys.value)
  for (const a of p.otherAssignments) {
    const k = rowRemovalKey(a.businessUnitId, a.roleId)
    if (checked) s.add(k)
    else s.delete(k)
  }
  selectedRemovalKeys.value = [...s]
}

const searchRemovalBeneficiaries = async (query: string) => {
  loadingRemovalBeneficiarySearch.value = true
  try {
    const res = (await permissionApi.searchUsersForDelegation({
      keyword: query || undefined,
      page: 0,
      size: 20
    })) as any
    const payload = res?.data ?? res
    removalBeneficiaryOptions.value = Array.isArray(payload?.content) ? payload.content : []
  } catch {
    removalBeneficiaryOptions.value = []
  } finally {
    loadingRemovalBeneficiarySearch.value = false
  }
}

const openRemovePermissionDialog = () => {
  removalBeneficiaryUserId.value = ''
  removalBeneficiaryOptions.value = []
  removalPayload.value = null
  selectedRemovalKeys.value = []
  activeFuCollapseNames.value = []
  removePermissionReason.value = ''
  removePermissionDialogVisible.value = true
}

const loadRemovalOptions = async () => {
  loadingRemovalOptions.value = true
  try {
    const res = (await permissionApi.getRemovalOptionsByFunctionUnit(
      removalBeneficiaryUserId.value || undefined
    )) as any
    const data = res?.data ?? res
    const payload: RemovalOptionsByFunctionUnitPayload = {
      functionUnitGroups: Array.isArray(data?.functionUnitGroups) ? data.functionUnitGroups : [],
      otherAssignments: Array.isArray(data?.otherAssignments) ? data.otherAssignments : []
    }
    removalPayload.value = payload
    selectedRemovalKeys.value = []
    activeFuCollapseNames.value = payload.functionUnitGroups.map((g) => g.functionUnitId)
  } catch (e: any) {
    const msg = e.response?.data?.message || e.message || t('permission.requestRemoveBuRoleFailed')
    ElMessage.error(msg)
    removalPayload.value = { functionUnitGroups: [], otherAssignments: [] }
  } finally {
    loadingRemovalOptions.value = false
  }
}

const submitRemovalBatch = async () => {
  if (!removePermissionReason.value.trim()) {
    ElMessage.warning(t('permission.enterReason'))
    return
  }
  if (selectedRemovalKeys.value.length === 0) {
    return
  }
  const reason = removePermissionReason.value.trim()
  const beneficiary = removalBeneficiaryUserId.value || undefined
  submittingRemovalBatch.value = true
  let ok = 0
  let fail = 0
  try {
    for (const key of selectedRemovalKeys.value) {
      const sep = key.indexOf('::')
      if (sep < 0) continue
      const businessUnitId = key.slice(0, sep)
      const roleId = key.slice(sep + 2)
      try {
        await permissionApi.requestBusinessUnitRoleRemoval({
          businessUnitId,
          roleId,
          reason,
          beneficiaryUserId: beneficiary
        })
        ok++
      } catch {
        fail++
      }
    }
    if (ok > 0) {
      ElMessage.success(t('permission.requestRemoveBuRoleSuccess'))
      removePermissionDialogVisible.value = false
      loadPendingRequests()
      loadHistoryRequests()
      loadMyBuRoles()
      loadExitBuMemberships()
      if (fail > 0) {
        ElMessage.warning(t('permission.removalPartialFailures'))
      }
    } else if (fail > 0) {
      ElMessage.error(t('permission.requestRemoveBuRoleFailed'))
    }
  } finally {
    submittingRemovalBatch.value = false
  }
}

const loadExitBuMemberships = async () => {
  loadingExitBu.value = true
  try {
    const res = await permissionApi.getMyMemberships()
    const data = (res as any)?.data?.data || (res as any)?.data || res
    const buMap = new Map<string, { businessUnitId: string; businessUnitName: string; joinedAt?: string }>()
    if (data?.businessUnitRoles) {
      for (const role of data.businessUnitRoles as UserBusinessUnitRole[]) {
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
      for (const bu of data.businessUnits as { businessUnitId?: string; id?: string; businessUnitName?: string; name?: string; joinedAt?: string }[]) {
        const id = bu.businessUnitId || bu.id
        if (id && !buMap.has(id)) {
          buMap.set(id, {
            businessUnitId: id,
            businessUnitName: bu.businessUnitName || bu.name || id,
            joinedAt: bu.joinedAt
          })
        }
      }
    }
    exitBuRows.value = Array.from(buMap.values())
  } catch (e) {
    console.error('Failed to load exit BU memberships:', e)
    exitBuRows.value = []
  } finally {
    loadingExitBu.value = false
  }
}

const searchExitBuBeneficiaries = async (query: string) => {
  loadingExitBuBeneficiarySearch.value = true
  try {
    const res = (await permissionApi.searchUsersForDelegation({
      keyword: query || undefined,
      page: 0,
      size: 20
    })) as any
    const payload = res?.data ?? res
    exitBuBeneficiaryOptions.value = Array.isArray(payload?.content) ? payload.content : []
  } catch {
    exitBuBeneficiaryOptions.value = []
  } finally {
    loadingExitBuBeneficiarySearch.value = false
  }
}

const openExitBuDialog = (row: { businessUnitId: string; businessUnitName: string }) => {
  exitBuForm.businessUnitId = row.businessUnitId
  exitBuForm.businessUnitName = row.businessUnitName
  exitBuForm.beneficiaryUserId = ''
  exitBuForm.reason = ''
  exitBuBeneficiaryOptions.value = []
  exitBuDialogVisible.value = true
}

const submitExitBu = async () => {
  if (!exitBuForm.reason.trim()) {
    ElMessage.warning(t('permission.enterReason'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('exitRole.exitBuConfirm', { bu: exitBuForm.businessUnitName || exitBuForm.businessUnitId }),
      t('common.confirm'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  exitBuSubmitting.value = true
  try {
    const body: { businessUnitId: string; reason: string; beneficiaryUserId?: string } = {
      businessUnitId: exitBuForm.businessUnitId,
      reason: exitBuForm.reason.trim()
    }
    const me = getStoredUser()?.userId
    if (exitBuForm.beneficiaryUserId && exitBuForm.beneficiaryUserId !== me) {
      body.beneficiaryUserId = exitBuForm.beneficiaryUserId
    }
    await permissionApi.requestBusinessUnitExit(body)
    ElMessage.success(t('exitRole.exitRequestSuccess'))
    exitBuDialogVisible.value = false
    loadExitBuMemberships()
    loadPendingRequests()
    loadHistoryRequests()
    loadMyBuRoles()
  } catch (e: unknown) {
    const err = e as { message?: string }
    ElMessage.error(err.message || t('exitRole.exitFailed'))
  } finally {
    exitBuSubmitting.value = false
  }
}

const loadApplicableBusinessUnits = async () => {
  loadingBusinessUnits.value = true
  try {
    const res = await permissionApi.getApplicableBusinessUnits() as any
    // axios 拦截器返回 response.data，即 ApiResponse { success, data: [...] }
    if (res?.data && Array.isArray(res.data)) {
      applicableBusinessUnits.value = res.data
    } else if (Array.isArray(res)) {
      applicableBusinessUnits.value = res
    } else {
      applicableBusinessUnits.value = []
    }
    if (applicableBusinessUnits.value.length === 0) {
      const cat = await permissionApi.getBusinessUnits() as any
      const raw = cat?.data ?? cat
      if (Array.isArray(raw)) {
        applicableBusinessUnits.value = raw.map((b: BusinessUnit) => ({
          id: b.id,
          name: b.name || b.id
        })) as BusinessUnit[]
      }
    }
  } catch (e) {
    console.error('Failed to load applicable business units:', e)
    applicableBusinessUnits.value = []
  } finally {
    loadingBusinessUnits.value = false
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

const canCancelAsBeneficiary = (row: PermissionRequestRecord) => {
  const me = getStoredUser()?.userId
  return !!(me && row.applicantId === me)
}

const loadEligibleRoles = async (businessUnitId: string) => {
  if (!businessUnitId) {
    eligibleRoles.value = []
    return
  }
  loadingRoles.value = true
  try {
    const res = await permissionApi.getBusinessUnitRoles(businessUnitId) as any
    if (res?.data && Array.isArray(res.data)) {
      eligibleRoles.value = res.data
    } else if (Array.isArray(res)) {
      eligibleRoles.value = res
    } else {
      eligibleRoles.value = []
    }
  } catch (e) {
    console.error('Failed to load eligible roles:', e)
    eligibleRoles.value = []
  } finally {
    loadingRoles.value = false
  }
}

// 状态和类型处理
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const getStatusType = (status: string): TagType => {
  const map: Record<string, TagType> = {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    CANCELLED: 'info'
  }
  return map[status] || 'info'
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PENDING: t('permission.pending'),
    APPROVED: t('permission.approved'),
    REJECTED: t('permission.rejected'),
    CANCELLED: t('permission.cancelled')
  }
  return map[status] || status
}

const getRequestTypeTag = (type: string): TagType => {
  const map: Record<string, TagType> = {
    VIRTUAL_GROUP: 'success',
    VIRTUAL_GROUP_JOIN: 'success',
    BUSINESS_UNIT: 'primary',
    BUSINESS_UNIT_JOIN: 'primary',
    BUSINESS_UNIT_ROLE: 'primary',
    BUSINESS_UNIT_ROLE_REMOVAL: 'warning',
    BUSINESS_UNIT_EXIT: 'danger',
    ROLE_ASSIGNMENT: 'info'
  }
  return map[type] || 'info'
}

const getRequestTypeLabel = (type: string | undefined) => {
  if (!type) return '-'
  const map: Record<string, string> = {
    VIRTUAL_GROUP: t('permission.virtualGroupJoin'),
    VIRTUAL_GROUP_JOIN: t('permission.virtualGroupJoin'),
    BUSINESS_UNIT: t('permission.businessUnitJoin'),
    BUSINESS_UNIT_JOIN: t('permission.businessUnitJoin'),
    BUSINESS_UNIT_ROLE: t('permission.businessUnitRole'),
    BUSINESS_UNIT_ROLE_REMOVAL: t('permission.businessUnitRoleRemoval'),
    BUSINESS_UNIT_EXIT: t('permission.businessUnitExit'),
    ROLE_ASSIGNMENT: t('permission.roleAssignment')
  }
  return map[type] || type
}

/** 「我的申请」列表接口返回 PermissionRequestListItem：仅有 targetId/targetName，无 businessUnit* 扁平字段 */
const meaningfulListTargetName = (row: any): string | undefined => {
  const n = row?.targetName
  if (typeof n !== 'string') return undefined
  const t = n.trim()
  if (t && t !== '-') return t
  return undefined
}

// 获取申请目标名称
const getTargetName = (row: any) => {
  if (!row) return '-'
  const listTn = meaningfulListTargetName(row)
  const listTid =
    row.targetId != null && String(row.targetId).trim() !== '' ? String(row.targetId).trim() : undefined

  if (row.requestType === 'BUSINESS_UNIT_EXIT') {
    return row.businessUnitName || listTn || row.businessUnitId || listTid || '-'
  }
  if (row.requestType === 'BUSINESS_UNIT_ROLE_REMOVAL') {
    const bu = row.businessUnitName || listTn || row.businessUnitId || listTid || ''
    const role =
      row.roleName ||
      row.roleId ||
      (Array.isArray(row.roleNames)
        ? row.roleNames.find((x: unknown) => x != null && String(x).trim() !== '')
        : undefined)
    const roleStr = role != null ? String(role).trim() : ''
    const joined = [bu, roleStr].filter(Boolean).join(' / ')
    return joined || '-'
  }
  if (listTn) return listTn
  if (row.targetName) return row.targetName
  if (row.virtualGroupName) return row.virtualGroupName
  if (row.businessUnitName) return row.businessUnitName
  if (row.roleName) return row.roleName
  return listTid || '-'
}

const formatDateTime = (dateStr: string) => {
  if (!dateStr) return '-'
  try {
    const date = new Date(dateStr)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch {
    return dateStr
  }
}

// 取消申请
const cancelRequest = async (row: PermissionRequestRecord) => {
  try {
    await ElMessageBox.confirm(t('permission.cancelConfirm'), t('common.warning'), {
      type: 'warning'
    })
    
    await permissionApi.cancelRequest(row.id)
    ElMessage.success(t('permission.cancelSuccess'))
    // Keep UI consistent immediately even if history API is paginated/filtered.
    const cancelledRecord: PermissionRequestRecord = {
      ...row,
      status: 'CANCELLED',
      updatedAt: new Date().toISOString()
    }
    pendingList.value = pendingList.value.filter(item => item.id !== row.id)
    historyList.value = [cancelledRecord, ...historyList.value.filter(item => item.id !== row.id)]
    // Refresh pending from server, but keep cancelled item visible in history list.
    loadPendingRequests()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(t('permission.cancelFailed'))
    }
  }
}

// 对话框操作
const showApplyDialog = () => {
  applyForm.beneficiaryUserId = ''
  applyForm.businessUnitId = ''
  applyForm.roleId = ''
  applyForm.reason = ''
  beneficiaryOptions.value = []
  eligibleRoles.value = []
  applyDialogVisible.value = true

  loadApplicableBusinessUnits()
}

const onBusinessUnitChange = async (businessUnitId: string) => {
  applyForm.roleId = ''
  await loadEligibleRoles(businessUnitId)
}

const submitApply = async () => {
  if (!applyForm.businessUnitId) {
    ElMessage.warning(t('permission.selectBusinessUnit'))
    return
  }

  if (!applyForm.roleId) {
    ElMessage.warning(t('permission.selectRole'))
    return
  }
  
  if (!applyForm.reason.trim()) {
    ElMessage.warning(t('permission.enterReason'))
    return
  }

  submitting.value = true
  try {
    const payload: Record<string, unknown> = {
      businessUnitId: applyForm.businessUnitId,
      roleIds: [applyForm.roleId],
      reason: applyForm.reason.trim()
    }
    if (applyForm.beneficiaryUserId) {
      payload.beneficiaryUserId = applyForm.beneficiaryUserId
    }
    await permissionApi.requestBusinessUnitRole(payload as any)
    ElMessage.success(t('permission.businessUnitRequestSuccess'))
    
    applyDialogVisible.value = false
    loadPendingRequests()
    loadHistoryRequests()
  } catch (e: any) {
    const msg = e.response?.data?.message || e.message || t('permission.requestFailed')
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

// 初始化
onMounted(async () => {
  await checkApproverStatus()
  loadMyBuRoles()
  loadExitBuMemberships()
  loadPendingRequests()
  loadHistoryRequests()
  if (isApprover.value) {
    loadApproverPending()
  }
  void pendingApprovalStore.fetchPendingCount()
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

  .my-bu-roles-card {
    margin-bottom: 20px;
  }

  .section-title {
    font-size: 16px;
    font-weight: 500;
    color: var(--text-primary);
    margin: 0 0 12px;
  }

  .section-sub {
    margin: 0 0 12px;
    color: var(--el-text-color-secondary);
    font-size: 13px;
    line-height: 1.5;
  }

  .table-foot-hint {
    margin: 10px 0 0;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.45;
  }

  .exit-bu-card {
    margin-bottom: 20px;

    .info-alert {
      margin-bottom: 12px;
    }
  }

  .request-lists-card {
    margin-bottom: 20px;

    &.approval-lists-card {
      border-top: 1px solid rgba(219, 0, 17, 0.12);
      padding-top: 4px;
    }
  }

  .list-tabs {
    :deep(.el-tabs__header) {
      margin-bottom: 12px;
    }
  }

  .proxy-tag {
    margin-left: 6px;
    vertical-align: middle;
  }

  :deep(.apply-form) {
    .el-form-item__label {
      white-space: nowrap;
    }

    .form-hint {
      margin-top: 6px;
      color: var(--el-text-color-secondary);
      font-size: 12px;
      line-height: 1.4;
    }
  }
}
</style>

<!-- Dialog 挂载到 body，无 scoped 以便样式生效 -->
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
