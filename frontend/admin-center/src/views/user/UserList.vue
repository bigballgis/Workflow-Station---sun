<template>
  <div class="page-container">
    <PageHeader :title="t('menu.userList')">
      <template v-if="canWriteUser" #actions>
        <el-button type="primary" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>{{ t('user.createUser') }}
        </el-button>
      </template>
    </PageHeader>
    
    <el-card class="search-card">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item :label="t('user.keyword')">
          <el-input v-model="query.keyword" :placeholder="t('user.keywordPlaceholder')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="query.status" :placeholder="t('user.selectStatus')" clearable style="width: 120px">
            <el-option :label="t('user.active')" value="ACTIVE" />
            <el-option :label="t('user.disabled')" value="DISABLED" />
            <el-option :label="t('user.locked')" value="LOCKED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>{{ t('common.search') }}
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>{{ t('common.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <el-card class="table-card">
      <el-table :data="users" v-loading="loading" stripe border table-layout="auto" style="width: 100%">
        <!-- <el-table-column prop="employeeId" :label="t('user.employeeId')" min-width="100" show-overflow-tooltip /> -->
        <el-table-column prop="username" :label="t('user.username')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="fullName" :label="t('user.fullName')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="email" :label="t('user.email')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="position" :label="t('user.position')" min-width="100" show-overflow-tooltip />
        <el-table-column :label="t('user.entityManager')" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.entityManagerName" class="manager-name">{{ row.entityManagerName }}</span>
            <span v-else class="no-manager">-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('user.functionManager')" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.functionManagerName" class="manager-name">{{ row.functionManagerName }}</span>
            <span v-else class="no-manager">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ t(userStatusKey(row.status)) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="200" fixed="right">
          <template #header>{{ t('common.actions') }}</template>
          <template #default="{ row }">
            <div style="display: flex; align-items: center; flex-wrap: nowrap; white-space: nowrap;">
            <el-button v-if="canWriteUser" link type="primary" size="small" @click="showEditDialog(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="primary" size="small" @click="showDetailDialog(row)">{{ t('common.view') }}</el-button>
            <el-dropdown v-if="canWriteUser" @command="(cmd: string) => handleCommand(row, cmd)">
              <el-button link type="primary" size="small">
                {{ t('common.operation') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="row.status !== 'ACTIVE'" command="enable">
                    <el-icon><CircleCheck /></el-icon>{{ t('common.enable') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'ACTIVE'" command="disable">
                    <el-icon><CircleClose /></el-icon>{{ t('common.disable') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'LOCKED'" command="unlock">
                    <el-icon><Unlock /></el-icon>{{ t('user.unlock') }}
                  </el-dropdown-item>
                  <el-dropdown-item command="resetPassword">
                    <el-icon><Key /></el-icon>{{ t('user.resetPassword') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="canDeleteUser" command="delete" divided>
                    <el-icon><Delete /></el-icon>{{ t('common.delete') }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
      
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="handleSearch"
        />
      </div>
    </el-card>
    
    <!-- User form dialog -->
    <UserFormDialog 
      v-model="formDialogVisible" 
      :user="currentUser" 
      @success="handleSearch" 
    />
    
    <!-- User detail dialog -->
    <UserDetailDialog
      v-model="detailDialogVisible"
      :user-id="currentUserId"
    />
    
    <!-- Batch import dialog -->
    <UserImportDialog
      v-model="importDialogVisible"
      @success="handleSearch"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { 
  Plus, Search, Refresh, ArrowDown, 
  CircleCheck, CircleClose, Unlock, Key, Delete 
} from '@element-plus/icons-vue'
import { statusTagType, userStatusKey } from '@/utils/format'
import { useUser } from '@/composables/modules/useUser'
import UserFormDialog from './components/UserFormDialog.vue'
import UserDetailDialog from './components/UserDetailDialog.vue'
import UserImportDialog from './components/UserImportDialog.vue'
import PageHeader from '@/components/PageHeader.vue'

const { t } = useI18n()

const {
  loading,
  users,
  total,
  query,
  formDialogVisible,
  detailDialogVisible,
  importDialogVisible,
  currentUser,
  currentUserId,
  canWriteUser,
  canDeleteUser,
  handleSearch,
  handleReset,
  showCreateDialog,
  showEditDialog,
  showDetailDialog,
  showImportDialog,
  handleCommand,
} = useUser()

onMounted(() => {
  handleSearch()
})
</script>
