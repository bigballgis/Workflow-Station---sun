<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="900px"
    top="8vh"
    class="call-relation-dialog"
    destroy-on-close
    @open="load"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">
          {{ t('callRelation.dialogTitle', { name: relations?.functionUnitName ?? '' }) }}
        </span>
        <span class="dialog-subtitle">{{ relations?.functionUnitCode }}</span>
      </div>
    </template>

    <el-tabs v-model="activeTab">
      <!-- What this unit calls -->
      <el-tab-pane name="calls">
        <template #label>
          {{ t('callRelation.tabCalls') }} · {{ calls.length }}
        </template>

        <el-table
          v-if="calls.length > 0"
          :data="calls"
          size="small"
          class="relation-table"
        >
          <el-table-column
            :label="t('callRelation.colTarget')"
            min-width="200"
          >
            <template #default="{ row }">
              <span class="target-name">
                {{ row.name || row.code || t('callRelation.unconfigured') }}
              </span>
              <!-- Problems that would only otherwise surface at deploy time. -->
              <div
                v-if="row.code && !row.id"
                class="row-warning"
              >
                {{ t('callRelation.missingTarget') }}
              </div>
              <div
                v-else-if="row.id && !row.callable"
                class="row-warning"
              >
                {{ t('callRelation.notCallable') }}
              </div>
              <div
                v-else-if="row.pinnedVersion && !row.pinnedVersionAvailable"
                class="row-warning"
              >
                {{ t('callRelation.pinnedVersionGone', { version: row.pinnedVersion }) }}
              </div>
            </template>
          </el-table-column>

          <el-table-column
            :label="t('callRelation.colPinnedVersion')"
            width="150"
          >
            <template #default="{ row }">
              <!-- Pinned means the callee's next publish does NOT change this call;
                   unpinned follows whatever is deployed. The difference is worth
                   stating rather than showing a blank cell. -->
              <el-tag
                v-if="row.pinnedVersion"
                size="small"
                :type="row.pinnedVersionAvailable ? 'success' : 'danger'"
                effect="plain"
              >
                {{ row.pinnedVersion }}
              </el-tag>
              <!-- Pinning stops the call following new publishes, which also means
                   nobody hears about them. Say so here, where the pin is visible. -->
              <div
                v-if="row.newerVersionAvailable"
                class="newer-version"
              >
                {{ t('callRelation.newerAvailable', { version: row.currentVersion }) }}
              </div>
              <span
                v-else
                class="follows-latest"
              >
                {{ t('callRelation.followsLatest') }}
                <template v-if="row.currentVersion"> · {{ row.currentVersion }}</template>
              </span>
            </template>
          </el-table-column>

          <el-table-column
            :label="t('callRelation.colCallNode')"
            min-width="170"
          >
            <template #default="{ row }">
              {{ row.callActivityName || row.callActivityId }}
              <el-tag
                v-if="row.multiInstance"
                size="small"
                type="info"
                effect="plain"
                class="per-row-tag"
              >
                {{ t('callRelation.perRow') }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column
            :label="t('callRelation.colActions')"
            min-width="230"
            class-name="actions-cell"
          >
            <template #default="{ row }">
              <!-- Up to three actions; they wrap rather than being clipped, since
                   the table's default single-line cell hid all but the first. -->
              <div class="row-actions">
              <el-button
                v-if="row.id"
                link
                type="primary"
                size="small"
                @click="openUnit(row.id, row.pinnedVersion)"
              >
                {{ t('callRelation.openTarget') }} ↗
              </el-button>
              <el-button
                link
                type="primary"
                size="small"
                @click="locateNode(row.callActivityId)"
              >
                {{ t('callRelation.locateNode') }}
              </el-button>
              <!-- Re-pins in place. Left to the designer to save, like any other
                   diagram edit, so it goes through the normal validate/deploy path. -->
              <el-button
                v-if="row.newerVersionAvailable && row.currentVersion"
                link
                type="warning"
                size="small"
                :disabled="readOnly"
                @click="repinToCurrent(row)"
              >
                {{ t('callRelation.updatePin', { version: row.currentVersion }) }}
              </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <el-empty
          v-else
          :description="t('callRelation.noCalls')"
          :image-size="80"
        />
      </el-tab-pane>

      <!-- What calls this unit — the half not visible from one's own diagram -->
      <el-tab-pane name="calledBy">
        <template #label>
          {{ t('callRelation.tabCalledBy') }} · {{ calledBy.length }}
        </template>

        <el-table
          v-if="calledBy.length > 0"
          :data="calledBy"
          size="small"
          class="relation-table"
        >
          <el-table-column
            :label="t('callRelation.colCaller')"
            min-width="220"
          >
            <template #default="{ row }">
              <span class="target-name">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('callRelation.colPinnedVersion')"
            width="150"
          >
            <template #default="{ row }">
              <el-tag
                v-if="row.pinnedVersion"
                size="small"
                type="success"
                effect="plain"
              >
                {{ row.pinnedVersion }}
              </el-tag>
              <span
                v-else
                class="follows-latest"
              >{{ t('callRelation.followsLatest') }}</span>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('callRelation.colCallNodeInCaller')"
            min-width="180"
          >
            <template #default="{ row }">
              {{ row.callActivityName || row.callActivityId }}
            </template>
          </el-table-column>
          <el-table-column
            :label="t('callRelation.colActions')"
            min-width="160"
          >
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                size="small"
                @click="openUnit(row.id)"
              >
                {{ t('callRelation.openCaller') }} ↗
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty
          v-else
          :description="t('callRelation.noCallers')"
          :image-size="80"
        />
      </el-tab-pane>

      <!-- The same relations as a picture -->
      <el-tab-pane name="graph">
        <template #label>
          {{ t('callRelation.tabGraph') }}
        </template>

        <div class="relation-graph">
          <div
            v-if="calledBy.length > 0"
            class="graph-row"
          >
            <div
              v-for="caller in calledBy"
              :key="caller.id + ':' + caller.callActivityId"
              class="graph-node graph-node--upstream"
              @click="openUnit(caller.id)"
            >
              <span class="graph-node-name">{{ caller.name }}</span>
              <span class="graph-node-meta">{{ caller.callActivityName }}</span>
            </div>
          </div>
          <div
            v-if="calledBy.length > 0"
            class="graph-arrow"
          >
            ↓
          </div>

          <div class="graph-row">
            <div class="graph-node graph-node--current">
              <span class="graph-node-name">{{ relations?.functionUnitName }}</span>
              <span class="graph-node-meta">{{ relations?.functionUnitCode }}</span>
            </div>
          </div>

          <div
            v-if="calls.length > 0"
            class="graph-arrow"
          >
            ↓
          </div>
          <div
            v-if="calls.length > 0"
            class="graph-row"
          >
            <div
              v-for="call in calls"
              :key="call.callActivityId"
              class="graph-node"
              :class="call.id ? 'graph-node--downstream' : 'graph-node--broken'"
              @click="call.id && openUnit(call.id)"
            >
              <span class="graph-node-name">
                {{ call.name || call.code || t('callRelation.unconfigured') }}
              </span>
              <span class="graph-node-meta">
                {{ call.callActivityName }}
                <template v-if="call.pinnedVersion"> · {{ call.pinnedVersion }}</template>
                <template v-else-if="call.multiInstance"> · {{ t('callRelation.perRow') }}</template>
              </span>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <div class="dialog-note">
      {{ t('callRelation.derivedNote') }}
    </div>

    <template #footer>
      <el-button @click="visible = false">
        {{ t('callRelation.closeAndReturn') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  functionUnitApi,
  type FunctionUnitCallRelations,
  type CalledUnitRelation,
} from '@/api/functionUnit'
import { setExtensionProperty } from '@/utils/bpmnExtensions'

const { t } = useI18n()
const router = useRouter()

const props = defineProps<{
  modelValue: boolean
  functionUnitId: number
  /** The live modeler, so "locate node" can select and centre the call step. */
  modeler?: any
  /** When the unit is read-only, re-pinning (a diagram edit) is not offered. */
  readOnly?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  /** A pin was changed on the canvas; the parent refreshes anything derived from it. */
  (e: 'repinned'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const relations = ref<FunctionUnitCallRelations | null>(null)
const activeTab = ref('calls')

const calls = computed(() => relations.value?.calls ?? [])
const calledBy = computed(() => relations.value?.calledBy ?? [])

const dialogTitle = computed(() => t('callRelation.dialogTitleShort'))

async function load() {
  if (!props.functionUnitId) return
  try {
    const res = await functionUnitApi.getCallRelations(props.functionUnitId)
    relations.value = (res as { data?: FunctionUnitCallRelations })?.data ?? null
    // A unit that is only ever called has nothing on the "Calls" tab; open where
    // its relations actually are.
    activeTab.value = calls.value.length === 0 && calledBy.value.length > 0 ? 'calledBy' : 'calls'
  } catch (error) {
    console.warn('Could not load Function Unit call relations', error)
    relations.value = null
  }
}

/**
 * Opens a related unit. When the call is pinned, the pinned version is opened
 * read-only — that is what the call runs; the live draft may already differ.
 */
function openUnit(id?: number, pinnedVersion?: string) {
  if (!id) return
  visible.value = false
  router.push({
    path: `/function-units/${id}`,
    query: pinnedVersion ? { version: pinnedVersion } : undefined,
  })
}

/**
 * Selects the call step on the canvas and scrolls it into view.
 *
 * Closing the dialog first is deliberate: the point of locating a node is to look
 * at it, which is impossible behind a modal.
 */
function locateNode(callActivityId: string) {
  visible.value = false
  if (!props.modeler || !callActivityId) return
  try {
    const elementRegistry = props.modeler.get('elementRegistry')
    const element = elementRegistry.get(callActivityId)
    if (!element) {
      ElMessage.warning(t('callRelation.nodeNotFound'))
      return
    }
    props.modeler.get('selection').select(element)
    props.modeler.get('canvas').scrollToElement(element)
  } catch (error) {
    console.warn('Could not locate call activity on the canvas', error)
  }
}

/**
 * Moves a pinned call onto the callee's current version.
 *
 * Edits the canvas rather than saving straight to the server, so the change goes
 * through the same auto-save, validation and deploy path as any other edit — and
 * can be undone with the canvas's own undo.
 */
function repinToCurrent(row: CalledUnitRelation) {
  if (!props.modeler || !row.currentVersion) return
  try {
    const element = props.modeler.get('elementRegistry').get(row.callActivityId)
    if (!element) {
      ElMessage.warning(t('callRelation.nodeNotFound'))
      return
    }
    setExtensionProperty(props.modeler, element, 'calledVersion', row.currentVersion)
    // Reflect it immediately; the server copy catches up on the next save.
    row.pinnedVersion = row.currentVersion
    row.pinnedVersionAvailable = true
    row.newerVersionAvailable = false
    ElMessage.success(t('callRelation.pinUpdated', { version: row.currentVersion }))
    emit('repinned')
  } catch (error) {
    console.warn('Could not update the pinned version', error)
  }
}

watch(() => props.functionUnitId, () => {
  if (visible.value) load()
})
</script>

<style scoped lang="scss">
$call-accent: #7c3aed;
$call-accent-soft: #f5f3ff;

.dialog-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.dialog-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.dialog-subtitle {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.relation-table {
  width: 100%;
}

.target-name {
  font-weight: 500;
}

.row-warning {
  font-size: 11px;
  color: var(--el-color-danger);
  margin-top: 2px;
}

.follows-latest {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.newer-version {
  font-size: 11px;
  color: var(--el-color-warning-dark-2);
  margin-top: 3px;
}

.per-row-tag {
  margin-left: 6px;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  column-gap: 2px;
  row-gap: 2px;

  .el-button + .el-button {
    margin-left: 0;
  }
}

/* el-table cells default to a single clipped line; actions need to wrap. */
:deep(.actions-cell .cell) {
  white-space: normal;
  overflow: visible;
  text-overflow: clip;
}

.dialog-note {
  margin-top: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

/* Graph tab: the same relations top-down, callers above, callees below. */
.relation-graph {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 16px 0;
  min-height: 240px;
}

.graph-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.graph-arrow {
  color: var(--el-text-color-secondary);
  font-size: 16px;
  line-height: 1;
}

.graph-node {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 16px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  min-width: 160px;

  &:hover {
    border-color: $call-accent;
  }

  &--current {
    border-color: $call-accent;
    background: $call-accent-soft;
    cursor: default;
  }

  &--upstream {
    border-left: 3px solid #0ea5e9;
  }

  &--downstream {
    border-left: 3px solid $call-accent;
  }

  &--broken {
    border-left: 3px solid var(--el-color-danger);
    cursor: default;
  }
}

.graph-node-name {
  font-size: 13px;
  font-weight: 500;
}

.graph-node-meta {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
</style>
