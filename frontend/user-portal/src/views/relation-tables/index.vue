<template>
  <div class="relation-table-list">
    <div class="page-header">
      <h2>Relation Tables</h2>
    </div>

    <div class="table-cards" v-loading="loading">
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="table in tables" :key="table.id">
          <el-card class="table-card" shadow="hover" @click="goToData(table)">
            <template #header>
              <div class="card-header">
                <span class="table-name">{{ table.displayName || table.tableName }}</span>
                <el-tag type="success" size="small">v{{ table.currentVersion }}</el-tag>
              </div>
            </template>
            <p class="table-desc">{{ table.description || 'No description' }}</p>
          </el-card>
        </el-col>
      </el-row>

      <el-empty v-if="tables.length === 0 && !loading" description="No relation tables available" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { relationTableApi, type RelationTableDTO } from '@/api/relationTable'

const router = useRouter()
const loading = ref(false)
const tables = ref<RelationTableDTO[]>([])

async function loadTables() {
  loading.value = true
  try {
    const res = await relationTableApi.getVisibleTables()
    tables.value = res.data || []
  } catch {
    tables.value = []
  } finally {
    loading.value = false
  }
}

function goToData(table: RelationTableDTO) {
  router.push(`/relation-tables/${table.id}`)
}

onMounted(loadTables)
</script>

<style lang="scss" scoped>
.relation-table-list {
  padding: 20px;

  .page-header {
    margin-bottom: 20px;
    h2 { margin: 0; font-size: 20px; }
  }

  .table-card {
    cursor: pointer;
    margin-bottom: 16px;
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      .table-name { font-weight: 500; }
    }
    .table-desc {
      color: #909399;
      font-size: 13px;
      margin: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}
</style>
