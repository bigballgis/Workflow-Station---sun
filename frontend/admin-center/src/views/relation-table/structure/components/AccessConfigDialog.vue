<template>
  <el-dialog
    :model-value="modelValue"
    :title="'Access Config - ' + (tableName || '')"
    width="720px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="access-config-header">
      <el-alert
        type="info"
        :closable="false"
        style="flex: 1; margin-right: 12px;"
      >
        Configure which roles can access this table in User Portal.
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
      :data="accessList"
      stripe
      empty-text="No access configured"
    >
      <el-table-column
        label="Role"
        min-width="180"
      >
        <template #default="{ row }">
          {{ resolveRoleName(row.targetId) }}
        </template>
      </el-table-column>
      <el-table-column
        label="Type"
        width="130"
      >
        <template #default="{ row }">
          <el-tag
            :type="resolveRoleTagType(row.targetId)"
            size="small"
          >
            {{ resolveRoleTypeLabel(row.targetId) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="Permission"
        width="160"
      >
        <template #default="{ row }">
          <el-select
            v-model="row.permissionLevel"
            size="small"
            style="width: 100%;"
            @change="handlePermissionLevelChange(row)"
          >
            <el-option
              label="Read & Write"
              value="READ_WRITE"
            />
            <el-option
              label="Readonly"
              value="READONLY"
            />
          </el-select>
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
      <el-form
        label-width="110px"
        label-position="left"
        style="margin-bottom: 4px;"
      >
        <el-form-item label="Permission">
          <el-radio-group v-model="selectedPermissionLevel">
            <el-radio value="READ_WRITE">
              Read &amp; Write
            </el-radio>
            <el-radio value="READONLY">
              Readonly
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <el-tabs
        v-model="addRoleTab"
        class="add-role-tabs"
      >
        <!-- Tab 1: BU cascade + BU roles -->
        <el-tab-pane
          label="BU Role"
          name="bu"
        >
          <p class="tab-hint">
            Select a Business Unit, then choose one or more of its bound roles.
          </p>
          <el-form
            label-width="110px"
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
                v-model="selectedBuRoleIds"
                multiple
                collapse-tags
                collapse-tags-tooltip
                filterable
                placeholder="Select one or more roles"
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

        <!-- Tab 2: System / Unbounded roles -->
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
              description="All system roles already have access"
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
          :loading="adding"
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
import { useRelationTableAccessConfig } from '@/composables/modules/useRelationTableAccessConfig'

const props = defineProps<{ modelValue: boolean; tableId?: number; tableName?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const {
  loading, accessList, rolesLoading,
  showAddRole, adding, addRoleTab, selectedSystemRoleIds, selectedBuId, selectedBuRoleIds, selectedPermissionLevel,
  buCascaderOptions, buRolesLoading, buCascaderProps,
  availableSystemRoles, availableBuRoles, roleTypeDisplayLabel,
  resolveRoleName, resolveRoleTagType, resolveRoleTypeLabel, formatDate,
  loadAccessList, loadAllRoles, resetAddForm, openAddDialog, handleBuChange, handleAddRole,
  handlePermissionLevelChange, handleRemove,
} = useRelationTableAccessConfig(toRef(props, 'tableId'))

watch(() => props.modelValue, val => { if (val) { loadAccessList(); loadAllRoles() } })
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
