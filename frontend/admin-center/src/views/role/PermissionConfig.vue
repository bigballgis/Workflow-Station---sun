<template>
  <div class="page-container">
    <PageHeader :title="t('menu.permissionConfig')" />
    
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card>
          <template #header>
            {{ t('role.roleList') }}
          </template>
          <el-input
            v-model="roleFilter"
            :placeholder="t('role.searchRole')"
            clearable
            style="margin-bottom: 15px"
          />
          <el-menu
            :default-active="selectedRoleId"
            @select="handleRoleSelect"
          >
            <el-menu-item
              v-for="role in filteredRoles"
              :key="role.id"
              :index="role.id"
            >
              <span>{{ role.name }}</span>
              <el-tag
                size="small"
                style="margin-left: 10px"
              >
                {{ role.memberCount }}
              </el-tag>
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>
      
      <el-col :span="16">
        <el-card
          v-if="selectedRole"
          v-loading="loading"
        >
          <template #header>
            <div class="permission-header">
              <span>{{ selectedRole.name }} - {{ t('permission.permissionConfig') }}</span>
              <el-button
                type="primary"
                size="small"
                :disabled="loading"
                @click="handleSave"
              >
                {{ t('permission.saveConfig') }}
              </el-button>
            </div>
          </template>
          
          <el-table
            :data="permissionMatrix"
            border
          >
            <el-table-column
              prop="name"
              :label="t('permission.resource')"
              width="200"
            />
            <el-table-column
              v-for="action in actions"
              :key="action"
              :label="t(permissionActionKey(action))"
              width="100"
              align="center"
            >
              <template #default="{ row }">
                <el-checkbox
                  v-model="row.permissions[action]"
                  :disabled="loading"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-empty
          v-else
          :description="t('permission.selectRoleToConfig')"
        />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import { permissionActionKey } from '@/utils/format'
import { usePermissionConfig } from '@/composables/modules/usePermissionConfig'

const { t } = useI18n()

const {
  roleFilter, selectedRole, actions, loading,
  permissionMatrix, filteredRoles, handleRoleSelect, handleSave,
} = usePermissionConfig()
</script>

<style scoped>
.permission-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
