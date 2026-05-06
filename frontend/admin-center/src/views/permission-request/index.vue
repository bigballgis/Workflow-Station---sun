<template>
  <div class="page-container">
    <PageHeader :title="t('permissionRequest.title')" />
    
    <el-card class="search-card">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item :label="t('permissionRequest.status')">
          <el-select v-model="query.status" :placeholder="t('permissionRequest.filterByStatus')" clearable style="width: 140px">
            <el-option :label="t('permissionRequest.pending')" value="PENDING" />
            <el-option :label="t('permissionRequest.approved')" value="APPROVED" />
            <el-option :label="t('permissionRequest.rejected')" value="REJECTED" />
            <el-option :label="t('permissionRequest.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('permissionRequest.requestType')">
          <el-select v-model="query.requestType" :placeholder="t('permissionRequest.filterByType')" clearable style="width: 160px">
            <el-option :label="t('permissionRequest.virtualGroup')" value="VIRTUAL_GROUP" />
            <el-option :label="t('permissionRequest.businessUnitRole')" value="BUSINESS_UNIT_ROLE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.dateRange')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :start-placeholder="t('common.startDate')"
            :end-placeholder="t('common.endDate')"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
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
      <el-table :data="requests" v-loading="loading" stripe border>
        <el-table-column prop="applicantName" :label="t('permissionRequest.applicant')" width="120">
          <template #default="{ row }">
            {{ row.applicantName || row.applicantUsername }}
          </template>
        </el-table-column>
        <el-table-column prop="requestType" :label="t('permissionRequest.requestType')" width="140">
          <template #default="{ row }">
            <el-tag :type="row.requestType === 'VIRTUAL_GROUP' ? 'success' : 'primary'" size="small">
              {{ row.requestType === 'VIRTUAL_GROUP' ? t('permissionRequest.virtualGroup') : t('permissionRequest.businessUnitRole') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetName" :label="t('permissionRequest.target')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="roleNames" :label="t('permissionRequest.roles')" min-width="150">
          <template #default="{ row }">
            <template v-if="row.roleNames?.length">
              <el-tag v-for="role in row.roleNames" :key="role" size="small" style="margin-right: 4px">{{ role }}</el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" :label="t('permissionRequest.reason')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="status" :label="t('permissionRequest.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approverName" :label="t('permissionRequest.approver')" width="100" />
        <el-table-column prop="approverComment" :label="t('permissionRequest.approverComment')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createdAt" :label="t('permissionRequest.createdAt')" width="160">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="approvedAt" :label="t('permissionRequest.approvedAt')" width="160">
          <template #default="{ row }">{{ row.approvedAt ? formatDate(row.approvedAt) : '-' }}</template>
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
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Search, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { usePermissionRequest } from '@/composables/modules/usePermissionRequest'

const { t } = useI18n()

const {
  loading, requests, total, dateRange, query,
  statusType, statusText, formatDate,
  handleSearch, handleReset,
} = usePermissionRequest()
</script>

