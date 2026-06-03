<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('gateway.apiCatalog') }}</span>
    </div>
    <el-card class="search-card">
      <div class="search-form">
        <el-input v-model="filterDomain" :placeholder="t('gateway.domain')" clearable style="width:180px" @change="fetchData" />
        <el-select v-model="filterEnv" :placeholder="t('gateway.environment')" clearable style="width:140px" @change="fetchData">
          <el-option label="DEV" value="DEV" /><el-option label="SIT" value="SIT" /><el-option label="UAT" value="UAT" /><el-option label="PROD" value="PROD" />
        </el-select>
      </div>
    </el-card>
    <el-card class="table-card">
      <el-table :data="apis" v-loading="loading" stripe @row-click="showDetail">
        <el-table-column prop="apiCode" :label="t('gateway.apiCode')" width="160" />
        <el-table-column prop="name" :label="t('gateway.apiName')" />
        <el-table-column prop="domain" :label="t('gateway.domain')" width="120" />
        <el-table-column prop="basePath" :label="t('gateway.basePath')" min-width="200" />
        <el-table-column prop="protocol" :label="t('gateway.protocol')" width="80" />
      </el-table>
      <div class="pagination-container">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" @current-change="fetchData" layout="prev,pager,next" />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" :title="t('gateway.apiDetail')" width="700px">
      <el-descriptions v-if="selectedApi" :column="2" border>
        <el-descriptions-item :label="t('gateway.apiCode')">{{ selectedApi.apiCode }}</el-descriptions-item>
        <el-descriptions-item :label="t('gateway.apiName')">{{ selectedApi.name }}</el-descriptions-item>
        <el-descriptions-item :label="t('gateway.domain')">{{ selectedApi.domain }}</el-descriptions-item>
        <el-descriptions-item :label="t('gateway.basePath')">{{ selectedApi.basePath }}</el-descriptions-item>
        <el-descriptions-item :label="t('gateway.protocol')">{{ selectedApi.protocol }}</el-descriptions-item>
        <el-descriptions-item :label="t('gateway.description')">{{ selectedApi.description || '-' }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="margin-top:16px">{{ t('gateway.versions') }}</h4>
      <el-table :data="selectedVersions" size="small">
        <el-table-column prop="version" :label="t('gateway.version')" width="100" />
        <el-table-column prop="upstreamRef" :label="t('gateway.upstreamRef')" />
        <el-table-column prop="lifecycleStatus" :label="t('gateway.lifecycleStatus')" width="120" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listCatalogApis, getCatalogApi } from '@/api/gateway'
import { ElMessage } from 'element-plus'

const { t } = useI18n()
const loading = ref(false), apis = ref<any[]>([]), page = ref(0), size = ref(20), total = ref(0)
const filterDomain = ref(''), filterEnv = ref('')
const detailVisible = ref(false), selectedApi = ref<any>(null), selectedVersions = ref<any[]>([])

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await listCatalogApis({ domain: filterDomain.value || undefined, environmentCode: filterEnv.value || undefined, page: page.value, size: size.value })
    apis.value = res.content || res.items || []
    total.value = res.totalElements || 0
  } catch (e: any) { ElMessage.error(e.response?.data?.error?.message || e.message || t('common.error')) }
  finally { loading.value = false }
}

const showDetail = async (row: any) => {
  try {
    const res: any = await getCatalogApi(row.id)
    selectedApi.value = res.api || res
    selectedVersions.value = res.versions || []
    detailVisible.value = true
  } catch (e: any) { ElMessage.error(e.message || t('common.error')) }
}

onMounted(fetchData)
</script>
