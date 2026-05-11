<template>
  <div class="relation-binding-panel">
    <div class="panel-header">
      <span class="title">Relation Table Bindings</span>
      <el-button
        type="primary"
        size="small"
        @click="showAddDialog = true"
      >
        <el-icon><Plus /></el-icon> Add Binding
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="bindings"
      size="small"
    >
      <el-table-column
        prop="displayName"
        label="Table"
        min-width="120"
      >
        <template #default="{ row }">
          <span>{{ row.displayName || row.tableName }}</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="bindingType"
        label="Type"
        width="100"
      >
        <template #default="{ row }">
          <el-tag
            type="warning"
            size="small"
          >
            {{ row.bindingType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="View"
        width="80"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            size="small"
            :disabled="!row.viewConfigId"
            @click="handleViewDesign(row)"
          >
            Design
          </el-button>
        </template>
      </el-table-column>
      <el-table-column
        label="Actions"
        width="80"
      >
        <template #default="{ row }">
          <el-button
            link
            type="danger"
            size="small"
            @click="handleUnbind(row)"
          >
            Unbind
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-if="bindings.length === 0 && !loading"
      description="No relation table bindings"
      :image-size="60"
    />

    <!-- Add binding dialog -->
    <el-dialog
      v-model="showAddDialog"
      title="Add Relation Table Binding"
      width="500px"
    >
      <el-table
        v-loading="loadingTables"
        :data="availableTables"
        size="small"
      >
        <el-table-column
          prop="displayName"
          label="Table Name"
          min-width="120"
        >
          <template #default="{ row }">
            <span>{{ row.displayName || row.tableName }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="status"
          label="Status"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              type="success"
              size="small"
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="Action"
          width="80"
        >
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              :disabled="isBound(row.id)"
              :loading="bindingTableId === row.id"
              @click="handleBind(row)"
            >
              {{ isBound(row.id) ? 'Bound' : 'Bind' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showAddDialog = false">
          Close
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  relationTableBindingApi,
  type RelationTableDTO,
  type RelationTableBindingDTO
} from '@/api/relationTable'

const props = defineProps<{
  formId: number
}>()

const emit = defineEmits<{
  (e: 'update'): void
  (e: 'design-view', binding: RelationTableBindingDTO): void
}>()

const loading = ref(false)
const loadingTables = ref(false)
const bindings = ref<RelationTableBindingDTO[]>([])
const availableTables = ref<RelationTableDTO[]>([])
const showAddDialog = ref(false)
const bindingTableId = ref<number | null>(null)

function isBound(tableId: number): boolean {
  return bindings.value.some(b => b.tableId === tableId)
}

async function loadBindings() {
  loading.value = true
  try {
    const res = await relationTableBindingApi.getBindings(props.formId)
    bindings.value = res.data || []
  } catch {
    bindings.value = []
  } finally {
    loading.value = false
  }
}

async function loadAvailableTables() {
  loadingTables.value = true
  try {
    const res = await relationTableBindingApi.getAvailableTables()
    availableTables.value = res.data || []
  } catch {
    availableTables.value = []
  } finally {
    loadingTables.value = false
  }
}

async function handleBind(table: RelationTableDTO) {
  bindingTableId.value = table.id
  try {
    await relationTableBindingApi.bind(props.formId, table.id)
    ElMessage.success('Binding created')
    await loadBindings()
    emit('update')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || 'Failed to bind')
  } finally {
    bindingTableId.value = null
  }
}

async function handleUnbind(binding: RelationTableBindingDTO) {
  await ElMessageBox.confirm('Are you sure to unbind this relation table?', 'Confirm', { type: 'warning' })
  try {
    await relationTableBindingApi.unbind(props.formId, binding.bindingId)
    ElMessage.success('Binding removed')
    await loadBindings()
    emit('update')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || 'Failed to unbind')
  }
}

function handleViewDesign(binding: RelationTableBindingDTO) {
  emit('design-view', binding)
}

watch(showAddDialog, (val) => {
  if (val) loadAvailableTables()
})

watch(() => props.formId, () => {
  if (props.formId) loadBindings()
}, { immediate: true })

onMounted(() => {
  if (props.formId) loadBindings()
})

defineExpose({ loadBindings, bindings })
</script>

<style lang="scss" scoped>
.relation-binding-panel {
  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    .title {
      font-weight: 500;
      font-size: 14px;
    }
  }
}
</style>
