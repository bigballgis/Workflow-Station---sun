<template>
  <div class="relation-table-data">
    <div class="page-header">
      <el-button @click="$router.push('/relation-tables')" text>
        <el-icon><ArrowLeft /></el-icon> Back
      </el-button>
      <h2>{{ tableName }}</h2>
      <el-button type="primary" @click="handleExport" :loading="exporting">
        <el-icon><Download /></el-icon> Export CSV
      </el-button>
    </div>

    <div class="search-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="Search..."
        clearable
        style="width: 300px"
        @keyup.enter="loadData"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <el-table :data="rows" v-loading="loading" size="small" border stripe>
      <el-table-column
        v-for="col in columns"
        :key="col"
        :prop="col"
        :label="col"
        min-width="120"
        show-overflow-tooltip
      />
    </el-table>

    <div class="pagination-bar" v-if="total > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>

    <el-empty v-if="rows.length === 0 && !loading" description="No data" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, Download, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { relationTableApi } from '@/api/relationTable'

const route = useRoute()
const tableId = computed(() => Number(route.params.tableId))
const tableName = ref('Relation Table')

const loading = ref(false)
const exporting = ref(false)
const rows = ref<Record<string, any>[]>([])
const columns = ref<string[]>([])
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const res = await relationTableApi.queryTableData(tableId.value, {
      page: currentPage.value - 1,
      size: pageSize.value,
      search: searchKeyword.value || undefined
    })
    const pageData = res.data
    rows.value = pageData?.content || []
    total.value = pageData?.totalElements || 0

    // Extract columns from first row
    if (rows.value.length > 0) {
      columns.value = Object.keys(rows.value[0])
    }
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  exporting.value = true
  try {
    const blob = await relationTableApi.exportCsv(tableId.value)
    const url = window.URL.createObjectURL(new Blob([blob as any]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `${tableName.value}.csv`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
    ElMessage.success('Export completed')
  } catch {
    ElMessage.error('Export failed')
  } finally {
    exporting.value = false
  }
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
.relation-table-data {
  padding: 20px;

  .page-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
    h2 { margin: 0; font-size: 18px; flex: 1; }
  }

  .search-bar {
    margin-bottom: 16px;
  }

  .pagination-bar {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
