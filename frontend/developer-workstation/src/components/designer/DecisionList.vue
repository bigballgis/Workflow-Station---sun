<template>
  <div class="decision-list">
    <div class="decision-list-header">
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon>
        {{ t('decision.create') }}
      </el-button>
    </div>

    <el-table v-if="decisions.length > 0" :data="decisions" v-loading="loading" style="width: 100%">
      <el-table-column prop="decisionKey" :label="t('decision.key')" min-width="150" />
      <el-table-column prop="decisionName" :label="t('decision.name')" min-width="150" />
      <el-table-column prop="hitPolicy" :label="t('decision.hitPolicy')" width="120" />
      <el-table-column :label="t('decision.boundNodes')" min-width="150">
        <template #default="{ row }">
          <template v-if="getBoundNodes(row.id).length > 0">
            <el-tag
              v-for="node in getBoundNodes(row.id)"
              :key="node.nodeId"
              size="small"
              type="success"
              style="margin-right: 4px;"
            >
              {{ node.nodeName }}
            </el-tag>
          </template>
          <span v-else class="text-muted">{{ t('decision.notBound') }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" :label="t('decision.lastUpdated')" width="180">
        <template #default="{ row }">
          {{ row.updatedAt || row.createdAt }}
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">
            {{ t('common.edit') }}
          </el-button>
          <el-button link type="warning" @click="handleBindToNode(row)">
            {{ t('decision.bindToNode') }}
          </el-button>
          <el-button link type="danger" @click="handleDelete(row)">
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-else-if="!loading" :description="t('decision.noDecisions')" />

    <!-- Create Decision Dialog -->
    <el-dialog v-model="showCreateDialog" :title="t('decision.create')" width="500px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item :label="t('decision.key')" required>
          <el-input v-model="createForm.decisionKey" :placeholder="t('decision.keyPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('decision.name')">
          <el-input v-model="createForm.decisionName" :placeholder="t('decision.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('decision.hitPolicy')">
          <el-select v-model="createForm.hitPolicy">
            <el-option label="FIRST" value="FIRST" />
            <el-option label="UNIQUE" value="UNIQUE" />
            <el-option label="ANY" value="ANY" />
            <el-option label="PRIORITY" value="PRIORITY" />
            <el-option label="COLLECT" value="COLLECT" />
            <el-option label="RULE ORDER" value="RULE_ORDER" />
            <el-option label="OUTPUT ORDER" value="OUTPUT_ORDER" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('decision.description')">
          <el-input v-model="createForm.description" type="textarea" :placeholder="t('decision.descriptionPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleCreate" :loading="creating">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Decision Designer Dialog -->
    <el-dialog v-model="showDesigner" :title="t('decision.edit')" fullscreen destroy-on-close>
      <DecisionDesigner
        v-if="editingDecisionId !== null"
        :function-unit-id="functionUnitId"
        :decision-id="editingDecisionId"
      />
    </el-dialog>

    <!-- Bind to Node Dialog -->
    <el-dialog v-model="showBindNodeDialog" :title="t('decision.bindToNode')" width="500px">
      <div v-if="serviceTaskNodes.length > 0">
        <el-radio-group v-model="selectedNodeId" style="display: flex; flex-direction: column; gap: 8px;">
          <el-radio
            v-for="node in serviceTaskNodes"
            :key="node.id"
            :value="node.id"
          >
            {{ node.name }}
          </el-radio>
        </el-radio-group>
      </div>
      <el-empty v-else :description="t('decision.noServiceTasks')" />
      <template #footer>
        <el-button @click="showBindNodeDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleConfirmBind" :disabled="!selectedNodeId">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { decisionApi } from '@/api/decision'
import type { DecisionDefinition, DecisionDefinitionRequest } from '@/api/decision'
import DecisionDesigner from './DecisionDesigner.vue'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import { parseBpmnServiceTasks, bindDecisionToNode } from './decisionListHelpers'

const { t } = useI18n()
const props = defineProps<{ functionUnitId: number }>()
const store = useFunctionUnitStore()

const decisions = ref<DecisionDefinition[]>([])
const loading = ref(false)
const creating = ref(false)
const showCreateDialog = ref(false)
const showDesigner = ref(false)
const editingDecisionId = ref<number | null>(null)

// Bind to Node state
const showBindNodeDialog = ref(false)
const bindingDecisionId = ref<number | null>(null)
const selectedNodeId = ref<string>('')
const serviceTaskNodes = ref<Array<{ id: string; name: string }>>([])
// Map: decisionId -> bound nodes
const decisionNodeBindings = ref<Map<number, Array<{ nodeId: string; nodeName: string }>>>(new Map())

const createForm = ref<DecisionDefinitionRequest>({
  decisionKey: '',
  decisionName: '',
  dmnXml: '',
  hitPolicy: 'FIRST',
  description: ''
})

async function loadDecisions() {
  loading.value = true
  try {
    const response = await decisionApi.list(props.functionUnitId) as unknown as { data: DecisionDefinition[] }
    decisions.value = response.data || []
  } catch (err) {
    decisions.value = []
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!createForm.value.decisionKey) return
  creating.value = true
  try {
    await decisionApi.create(props.functionUnitId, createForm.value)
    ElMessage.success(t('decision.createSuccess'))
    showCreateDialog.value = false
    createForm.value = { decisionKey: '', decisionName: '', dmnXml: '', hitPolicy: 'FIRST', description: '' }
    await loadDecisions()
  } catch (err) {
    ElMessage.error(t('common.error'))
  } finally {
    creating.value = false
  }
}

function handleEdit(row: DecisionDefinition) {
  editingDecisionId.value = row.id
  showDesigner.value = true
}

async function handleDelete(row: DecisionDefinition) {
  try {
    await ElMessageBox.confirm(t('decision.deleteConfirm'), t('decision.confirmTitle'), { type: 'warning' })
    await decisionApi.delete(props.functionUnitId, row.id)
    ElMessage.success(t('decision.deleteSuccess'))
    await loadDecisions()
  } catch (err) {
    // User cancelled or delete failed
    if ((err as string) !== 'cancel') {
      ElMessage.error(t('common.error'))
    }
  }
}

// ─── Bind to Node ─────────────────────────────────────────────────────────────

function getBoundNodes(decisionId: number): Array<{ nodeId: string; nodeName: string }> {
  return decisionNodeBindings.value.get(decisionId) || []
}

/**
 * Parse bindings from current BPMN XML in store.
 */
function parseBindingsFromBpmn() {
  const bpmnXml = store.process?.bpmnXml
  if (!bpmnXml) return

  const { bindings } = parseBpmnServiceTasks(bpmnXml)
  decisionNodeBindings.value = bindings
}

function handleBindToNode(row: DecisionDefinition) {
  const bpmnXml = store.process?.bpmnXml
  if (!bpmnXml) {
    ElMessage.warning(t('decision.noProcessDefined'))
    return
  }

  bindingDecisionId.value = row.id
  selectedNodeId.value = ''

  const { nodes } = parseBpmnServiceTasks(bpmnXml)
  serviceTaskNodes.value = nodes
  showBindNodeDialog.value = true
}

async function handleConfirmBind() {
  if (!bindingDecisionId.value || !selectedNodeId.value || !store.process?.bpmnXml) return

  try {
    const newXml = bindDecisionToNode(store.process.bpmnXml, selectedNodeId.value, bindingDecisionId.value)
    await store.saveProcess(props.functionUnitId, {
      ...store.process,
      bpmnXml: newXml
    })
    parseBindingsFromBpmn()
    showBindNodeDialog.value = false
    ElMessage.success(t('decision.bindSuccess'))
  } catch {
    ElMessage.error(t('common.error'))
  }
}

onMounted(() => {
  loadDecisions()
  parseBindingsFromBpmn()
})
</script>

<style scoped lang="scss">
.decision-list-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}
</style>
