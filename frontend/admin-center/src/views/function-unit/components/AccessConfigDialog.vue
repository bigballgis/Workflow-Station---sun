<template>
  <el-dialog
    :model-value="modelValue"
    :title="'Access Config - ' + (functionUnitName || '')"
    width="720px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-tabs v-model="grantTab">
      <el-tab-pane
        label="Access"
        name="access"
      />
      <el-tab-pane
        label="Audit"
        name="audit"
      />
    </el-tabs>

    <div class="access-config-header">
      <el-alert
        type="info"
        :closable="false"
        style="flex: 1; margin-right: 12px;"
      >
        <template v-if="grantTab === 'access'">
          Configure which roles can access this function unit in User Portal.
        </template>
        <template v-else>
          Configure which roles may review every request of this function unit in User
          Portal. Audit is read-and-comment only — it does not allow raising requests.
        </template>
      </el-alert>
      <el-button
        type="primary"
        size="small"
        @click="openAddDialog"
      >
        <el-icon><Plus /></el-icon>Add Role
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="currentGrantList"
      stripe
      :empty-text="grantTab === 'access' ? 'No access configured' : 'No audit access configured'"
    >
      <el-table-column
        label="Role"
        min-width="180"
      >
        <template #default="{ row }">
          {{ resolveRoleName(row.targetId || row.roleId, row.targetName || row.roleName) }}
        </template>
      </el-table-column>
      <el-table-column
        label="Type"
        width="140"
      >
        <template #default="{ row }">
          <el-tag
            :type="resolveRoleTagType(row.targetId || row.roleId)"
            size="small"
          >
            {{ resolveRoleTypeLabel(row.targetId || row.roleId) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        label="Created At"
        width="170"
      >
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="createdBy"
        label="Created By"
        width="120"
      />
      <el-table-column
        label="Actions"
        width="80"
        align="center"
      >
        <template #default="{ row }">
          <el-button
            link
            type="danger"
            size="small"
            @click="handleRemove(row)"
          >
            Delete
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">
        Close
      </el-button>
    </template>

    <!-- Add Role Sub-Dialog -->
    <el-dialog
      v-model="showAddRole"
      title="Add Role Access"
      width="560px"
      append-to-body
      @closed="resetAddForm"
    >
      <el-tabs
        v-model="addRoleTab"
        class="add-role-tabs"
      >
        <!-- Tab 1: BU cascade + BU-bounded roles -->
        <el-tab-pane
          label="BU Role"
          name="bu"
        >
          <p class="tab-hint">
            Select a Business Unit, then choose one of its bound roles.
          </p>
          <el-form
            label-width="auto"
            label-position="left"
            style="margin-top: 8px;"
          >
            <el-form-item
              label="Business Unit"
              required
            >
              <el-cascader
                v-model="selectedBuId"
                :options="buCascaderOptions"
                :props="buCascaderProps"
                filterable
                clearable
                placeholder="Select BU"
                style="width: 100%;"
                @change="handleBuChange"
              />
            </el-form-item>
            <el-form-item
              label="BU Role"
              required
            >
              <el-select
                v-model="selectedBuRoleId"
                filterable
                placeholder="Select a role"
                style="width: 100%;"
                :loading="buRolesLoading"
                :disabled="!selectedBuId"
              >
                <el-option
                  v-for="item in availableBuRoles"
                  :key="item.id"
                  :label="item.name || item.code"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- Tab 2: System / non-BU-bounded roles -->
        <el-tab-pane
          label="System Role"
          name="system"
        >
          <p class="tab-hint">
            All available system roles are pre-selected. Uncheck any you do not want to grant.
          </p>
          <div
            v-loading="rolesLoading"
            class="system-role-list"
          >
            <el-empty
              v-if="!rolesLoading && availableSystemRoles.length === 0"
              description="All eligible system roles already have access"
              :image-size="40"
            />
            <el-checkbox-group
              v-else
              v-model="selectedSystemRoleIds"
            >
              <div
                v-for="role in availableSystemRoles"
                :key="role.id"
                class="role-checkbox-item"
              >
                <el-checkbox :value="role.id">
                  <span class="role-checkbox-name">{{ role.name }}</span>
                  <el-tag
                    size="small"
                    type="info"
                    style="margin-left: 6px;"
                  >
                    {{ roleTypeDisplayLabel(role.type) }}
                  </el-tag>
                </el-checkbox>
              </div>
            </el-checkbox-group>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="showAddRole = false">
          Cancel
        </el-button>
        <el-button
          type="primary"
          :loading="addLoading"
          @click="handleAddRole"
        >
          Confirm
        </el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { watch, toRef } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { roleTypeDisplayLabel } from '@/utils/format'
import { useFunctionUnitAccessConfig } from '@/composables/modules/useFunctionUnitAccessConfig'

const props = defineProps<{ modelValue: boolean; functionUnitId?: string; functionUnitName?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const {
  loading, rolesLoading, grantTab, currentGrantList,
  showAddRole, addLoading, addRoleTab, selectedSystemRoleIds, selectedBuId, selectedBuRoleId,
  buCascaderOptions, buRolesLoading, buCascaderProps,
  availableSystemRoles, availableBuRoles,
  resolveRoleName, resolveRoleTypeLabel, resolveRoleTagType, formatDate,
  resetAddForm, fetchAccessConfig, fetchAllRoles, openAddDialog, handleBuChange, handleAddRole, handleRemove,
} = useFunctionUnitAccessConfig(toRef(props, 'functionUnitId'))

watch(() => props.modelValue, async (val) => {
  if (val) await Promise.all([fetchAccessConfig(), fetchAllRoles()])
})
</script>

<style scoped>
.access-config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.add-role-tabs {
  min-height: 160px;
}
.tab-hint {
  font-size: 13px;
  color: #909399;
  margin: 0 0 8px;
  line-height: 1.5;
}
.system-role-list {
  max-height: 220px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  padding: 8px 12px;
}
.role-checkbox-item {
  padding: 5px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.role-checkbox-item:last-child {
  border-bottom: none;
}
.role-checkbox-name {
  font-size: 13px;
}
</style>
