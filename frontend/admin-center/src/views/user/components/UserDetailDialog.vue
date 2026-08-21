<template>
  <el-dialog 
    :model-value="modelValue" 
    :title="t('common.view')" 
    width="820px" 
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div
      v-loading="loading"
      class="user-detail"
    >
      <template v-if="user">
        <el-descriptions
          :column="2"
          border
        >
          <el-descriptions-item :label="t('user.username')">
            {{ user.username }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('user.fullName')">
            {{ user.fullName }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('user.email')">
            {{ user.email }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('user.employeeId')">
            {{ user.employeeId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('user.position')">
            {{ user.position || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('user.status')">
            <el-tag
              :type="statusType(user.status)"
              size="small"
            >
              {{ statusText(user.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('user.entityManager')">
            {{ user.entityManagerName || t('user.notSet') }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('user.functionManager')">
            {{ user.functionManagerName || t('user.notSet') }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.createTime')">
            {{ formatDate(user.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('user.lastLogin')">
            {{ user.lastLoginAt ? formatDate(user.lastLoginAt) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item
            :label="t('user.lastLoginIp')"
            :span="2"
          >
            {{ user.lastLoginIp || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-tabs
          v-model="detailActiveTab"
          class="detail-tabs"
        >
          <el-tab-pane
            :label="t('user.detailTabPortalOrg')"
            name="portal"
          >
            <p class="tab-lead">
              {{ t('user.portalOrgTabHint') }}
            </p>

            <div class="section-title">
              {{ t('user.businessUnits') }}
            </div>
            <el-table
              v-if="businessUnits.length"
              :data="businessUnits"
              border
              size="small"
            >
              <el-table-column
                prop="name"
                :label="t('businessUnit.name')"
              />
              <el-table-column
                prop="code"
                :label="t('businessUnit.code')"
                width="150"
              />
              <el-table-column
                prop="path"
                :label="t('businessUnit.path')"
                show-overflow-tooltip
              />
            </el-table>
            <el-empty
              v-else
              :description="t('user.noBusinessUnits')"
              :image-size="60"
            />
            <div class="section-hint">
              {{ t('user.businessUnitHint') }}
            </div>

            <div class="section-title">
              {{ t('user.portalVirtualGroupsSection') }}
            </div>
            <el-table
              v-if="portalVirtualGroups.length"
              :data="portalVirtualGroups"
              border
              size="small"
            >
              <el-table-column
                prop="groupName"
                :label="t('virtualGroup.name')"
              />
              <el-table-column
                prop="groupDescription"
                :label="t('common.description')"
              />
              <el-table-column
                prop="joinedAt"
                :label="t('user.joinedAt')"
                width="170"
              >
                <template #default="{ row }">
                  {{ formatDate(row.joinedAt) }}
                </template>
              </el-table-column>
            </el-table>
            <el-empty
              v-else
              :description="t('user.noPortalVirtualGroups')"
              :image-size="60"
            />
            <div class="section-hint">
              {{ t('user.portalVirtualGroupHint') }}
            </div>

            <div class="section-title">
              {{ t('user.buRoleAssignments') }}
            </div>
            <div class="ubr-toolbar">
              <el-button
                type="primary"
                size="small"
                @click="openAssignBuRole"
              >
                {{ t('user.assignBuRole') }}
              </el-button>
            </div>
            <template v-if="buRoleGroups.length">
              <div
                v-for="g in buRoleGroups"
                :key="g.businessUnitId"
                class="ubr-group"
              >
                <div class="ubr-group-title">
                  {{ g.businessUnitName }}
                </div>
                <el-table
                  :data="g.rows"
                  border
                  size="small"
                >
                  <el-table-column
                    prop="roleName"
                    :label="t('user.roleName')"
                  />
                  <el-table-column
                    prop="roleCode"
                    :label="t('user.roleCode')"
                    width="160"
                  />
                  <el-table-column
                    :label="t('common.operation')"
                    width="100"
                    align="center"
                  >
                    <template #default="{ row }">
                      <el-button
                        type="danger"
                        link
                        size="small"
                        @click="removeBuRole(row)"
                      >
                        {{ t('user.removeBuRole') }}
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
            <el-empty
              v-else
              :description="t('user.noBuRoles')"
              :image-size="60"
            />
            <div class="section-hint">
              {{ t('user.buRoleHint') }}
            </div>
          </el-tab-pane>

          <el-tab-pane
            :label="t('user.detailTabPlatform')"
            name="platform"
          >
            <p class="tab-lead">
              {{ t('user.platformAccessTabHint') }}
            </p>

            <div class="section-title">
              {{ t('user.platformVirtualGroupsSection') }}
            </div>
            <el-table
              v-if="platformVirtualGroups.length"
              :data="platformVirtualGroups"
              border
              size="small"
            >
              <el-table-column
                prop="groupName"
                :label="t('virtualGroup.name')"
              />
              <el-table-column
                prop="groupDescription"
                :label="t('common.description')"
              />
              <el-table-column
                prop="joinedAt"
                :label="t('user.joinedAt')"
                width="170"
              >
                <template #default="{ row }">
                  {{ formatDate(row.joinedAt) }}
                </template>
              </el-table-column>
            </el-table>
            <el-empty
              v-else
              :description="t('user.noPlatformVirtualGroups')"
              :image-size="60"
            />
            <div class="section-hint">
              {{ t('user.platformVirtualGroupHint') }}
            </div>

            <div class="section-title">
              {{ t('user.platformRolesSection') }}
            </div>
            <el-table
              v-if="platformRoles.length"
              :data="platformRoles"
              border
              size="small"
            >
              <el-table-column
                prop="name"
                :label="t('user.roleName')"
              />
              <el-table-column
                prop="code"
                :label="t('user.roleCode')"
                width="160"
              />
              <el-table-column
                prop="type"
                :label="t('user.roleTypeColumn')"
                width="130"
              >
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    :type="getPlatformRoleTagType(row.type)"
                  >
                    {{ row.type || '—' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-empty
              v-else
              :description="t('user.noPlatformRoles')"
              :image-size="60"
            />
          </el-tab-pane>
        </el-tabs>

        <!-- 登录历史 -->
        <div class="section-title">
          {{ t('user.loginHistory') }}
        </div>
        <el-table
          v-if="successfulLoginHistory.length"
          :data="successfulLoginHistory"
          border
          size="small"
          max-height="200"
        >
          <el-table-column
            prop="loginTime"
            :label="t('user.loginTime')"
            width="170"
          >
            <template #default="{ row }">
              {{ formatDate(row.loginTime) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="loginPlatform"
            :label="t('user.loginPlatform')"
            width="130"
          >
            <template #default="{ row }">
              {{ formatLoginPlatform(row.loginPlatform) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="success"
            :label="t('user.loginStatus')"
            width="80"
            align="center"
          >
            <template #default="{ row }">
              <el-tag
                :type="row.success ? 'success' : 'danger'"
                size="small"
              >
                {{ row.success ? t('common.success') : t('common.failed') }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty
          v-else
          :description="t('user.noLoginHistory')"
          :image-size="60"
        />
      </template>
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">
        {{ t('common.close') }}
      </el-button>
    </template>

    <el-dialog
      v-model="assignDialogVisible"
      :title="t('user.assignBuRole')"
      width="480px"
      destroy-on-close
      append-to-body
      @closed="resetAssignDialog"
    >
      <el-form
        label-width="auto"
        label-position="left"
      >
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
      <p
        v-if="assignRoleLoaded && !assignRoleOptions.length"
        class="ubr-empty-hint"
      >
        {{ t('user.noEligibleRolesForBu') }}
      </p>
      <template #footer>
        <el-button @click="assignDialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="assignSubmitting"
          @click="submitAssignBuRole"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { watch, toRef, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserDetail } from '@/composables/modules/useUserDetail'

const props = defineProps<{ modelValue: boolean; userId: string }>()
defineEmits(['update:modelValue'])
const { t } = useI18n()

const formatLoginPlatform = (platform?: string) => {
  if (platform === 'ADMIN_CENTER') {
    return t('user.loginPlatformAdminCenter')
  }
  if (platform === 'USER_PORTAL') {
    return t('user.loginPlatformUserPortal')
  }
  return platform || t('user.notSet')
}

const { loading, detailActiveTab, user, businessUnits, portalVirtualGroups, platformVirtualGroups,
  platformRoles, buRoleGroups, assignDialogVisible, assignRoleLoading, assignSubmitting,
  assignRoleOptions, assignRoleLoaded, assignForm,
  getPlatformRoleTagType, statusType, statusText, formatDate,
  loadDetail, resetAssignDialog, onAssignBuChange, openAssignBuRole, submitAssignBuRole, removeBuRole,
} = useUserDetail(toRef(props, 'userId'))

const successfulLoginHistory = computed(() =>
  (user.value?.loginHistory ?? []).filter((row) => row.success),
)

watch(() => props.modelValue, async (val) => {
  if (val && props.userId) { detailActiveTab.value = 'portal'; await loadDetail() }
})
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
