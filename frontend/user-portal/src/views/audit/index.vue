<template>
  <div class="audit-page">
    <div class="page-header">
      <h1>{{ t('audit.title') }}</h1>
      <span
        v-if="functionUnitName"
        class="page-header__subtitle"
      >{{ functionUnitName }}</span>
    </div>

    <div class="portal-card">
      <el-alert
        v-if="forbidden"
        type="error"
        :title="t('audit.noAccess')"
        :description="t('audit.noAccessHint')"
        :closable="false"
        show-icon
      />

      <template v-else>
        <el-tabs
          v-model="activeTab"
          @tab-change="handleTabChange"
        >
          <el-tab-pane
            :label="t('common.all')"
            name="all"
          />
          <el-tab-pane
            :label="t('application.running')"
            name="RUNNING"
          />
          <el-tab-pane
            :label="t('application.completed')"
            name="COMPLETED"
          />
          <el-tab-pane
            :label="t('application.withdrawn')"
            name="WITHDRAWN"
          />
          <el-tab-pane
            :label="t('application.rejected')"
            name="REJECTED"
          />
        </el-tabs>

        <el-table
          :data="applicationList"
          stripe
          class="application-table"
          table-layout="fixed"
        >
          <template #empty>
            <div
              v-if="loading"
              class="table-empty-loading"
            >
              <el-icon class="table-empty-loading__icon is-loading">
                <Loading />
              </el-icon>
              <span>{{ t('common.loading') }}</span>
            </div>
            <span v-else>{{ t('audit.noRequests') }}</span>
          </template>
          <el-table-column
            prop="requestId"
            :label="t('application.requestId')"
            min-width="130"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <el-link
                type="primary"
                @click="viewDetail(row)"
              >
                {{ row.requestId || '-' }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column
            prop="businessKey"
            :label="t('application.processTitle')"
            min-width="160"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.businessKey || row.processDefinitionName }}
            </template>
          </el-table-column>
          <!-- Reviewers see other people's requests, so who raised it matters here
               in a way it never does on My Requests. -->
          <el-table-column
            prop="startUserName"
            :label="t('audit.initiator')"
            width="140"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.startUserName || row.startUserId || '-' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="currentStepName"
            :label="t('application.currentStep')"
            min-width="100"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.currentStepName || row.currentNode || '-' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="currentAssignee"
            :label="t('application.currentAssignee')"
            width="120"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.currentAssignee || '-' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="startTime"
            :label="t('application.startTime')"
            width="160"
          >
            <template #default="{ row }">
              {{ formatDate(row.startTime) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="status"
            :label="t('application.status')"
            width="90"
            align="center"
          >
            <template #default="{ row }">
              <el-tag
                :type="getStatusType(row.status)"
                size="small"
                effect="light"
              >
                {{ getStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :disabled="loading"
          :total="pagination.total"
          layout="total, prev, pager, next"
          style="margin-top: 16px; justify-content: flex-end;"
          @current-change="handlePageChange"
        />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Loading } from '@element-plus/icons-vue'
import { formatDate } from '@/utils/dateFormat'
import { processApi, type AuditFunctionUnit } from '@/api/process'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const activeTab = ref('all')
const loading = ref(true)
/**
 * The router guard cannot express a per-function-unit grant, so access is only
 * known once the list call answers. Until then the page shows its loading state
 * rather than an empty table that looks like "no requests".
 */
const forbidden = ref(false)
const pagination = reactive({ page: 1, size: 20, total: 0 })
const applicationList = ref<any[]>([])
const auditFunctionUnits = ref<AuditFunctionUnit[]>([])

const functionUnitCode = computed(() => String(route.params.functionUnitCode || ''))
const functionUnitName = computed(
  () => auditFunctionUnits.value.find(fu => fu.functionUnitCode === functionUnitCode.value)?.functionUnitName || ''
)

const getStatusType = (status: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' => {
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
    RUNNING: 'warning',
    COMPLETED: 'success',
    WITHDRAWN: 'info',
    REJECTED: 'danger'
  }
  return map[status] || 'info'
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    RUNNING: t('application.running'),
    COMPLETED: t('application.completed'),
    WITHDRAWN: t('application.withdrawn'),
    REJECTED: t('application.rejected')
  }
  return map[status] || status
}

const loadFunctionUnits = async () => {
  try {
    const res = await processApi.getAuditFunctionUnits()
    auditFunctionUnits.value = res.data || []
  } catch {
    auditFunctionUnits.value = []
  }
}

const loadApplications = async () => {
  if (!functionUnitCode.value) {
    applicationList.value = []
    loading.value = false
    return
  }
  loading.value = true
  forbidden.value = false
  try {
    const status = activeTab.value === 'all' ? undefined : activeTab.value
    const response: any = await processApi.getFunctionUnitApplications({
      functionUnitCode: functionUnitCode.value,
      page: pagination.page - 1,
      size: pagination.size,
      status
    })
    applicationList.value = response.data?.records || response.data?.content || []
    pagination.total = response.data?.total ?? response.data?.totalElements ?? 0
  } catch (e: any) {
    applicationList.value = []
    pagination.total = 0
    // 403 here means no audit grant on this unit — a permission answer, not a failure.
    if (e?.response?.status === 403 || e?.response?.data?.error?.code === '403') {
      forbidden.value = true
    }
  } finally {
    loading.value = false
  }
}

const handleTabChange = () => {
  pagination.page = 1
  loadApplications()
}

const handlePageChange = (page: number) => {
  pagination.page = page
  loadApplications()
}

/** Reviewers read a request through the same detail page its initiator sees;
 *  `from=audit` lets the shared detail page's breadcrumb say "All Requests" instead
 *  of always defaulting to "My Requests". */
const viewDetail = (row: any) => {
  if (!row.id) return
  router.push(`/applications/${row.id}?from=audit`)
}

// Switching unit in the menu keeps the route component mounted.
watch(functionUnitCode, () => {
  pagination.page = 1
  activeTab.value = 'all'
  loadApplications()
})

onMounted(() => {
  void loadFunctionUnits()
  void loadApplications()
})
</script>

<style scoped lang="scss">
.audit-page {
  padding: 0;
}

.page-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;

  h1 {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
  }

  &__subtitle {
    color: var(--el-text-color-secondary);
    font-size: 14px;
  }
}

.table-empty-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
}
</style>
