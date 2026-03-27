<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">Table Data Management</span>
      <el-button :loading="loading" @click="loadTables">Refresh</el-button>
    </div>

    <el-card class="content-card" v-loading="loading">
      <el-empty
        v-if="tables.length === 0"
        description="No deployed relation tables found"
      />
      <el-table
        v-else
        :data="tables"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="tableName" label="Table Name" min-width="180" show-overflow-tooltip />
        <el-table-column prop="displayName" label="Display Name" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="Status" width="120" />
        <el-table-column prop="updatedAt" label="Updated At" min-width="180" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { relationTableDataApi, type RelationTableResponse } from '@/api/relationTable'

const loading = ref(false)
const tables = ref<RelationTableResponse[]>([])

const loadTables = async () => {
  loading.value = true
  try {
    tables.value = await relationTableDataApi.getDeployedTables()
  } catch (error: any) {
    ElMessage.error(error?.message || 'Failed to load deployed relation tables')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTables()
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  .page-title {
    font-size: 20px;
    font-weight: 600;
    color: #303133;
  }
}
</style>
