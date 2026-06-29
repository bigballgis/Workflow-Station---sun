<template>
  <div class="page-container">
    <div class="card">
      <div
        class="flex-between"
        style="margin-bottom: 16px;"
      >
        <div
          class="flex"
          style="align-items: center; gap: 16px;"
        >
          <el-button @click="router.back()">
            <el-icon><ArrowLeft /></el-icon>
            {{ t('common.back') }}
          </el-button>
          <el-tooltip
            :content="store.current?.description || t('functionUnit.noDescription')"
            placement="bottom"
          >
            <IconPreview 
              :icon-id="store.current?.icon?.id" 
              size="large" 
            />
          </el-tooltip>
          <h3>{{ store.current?.name }}</h3>
          <el-tag :type="statusTagType(store.current?.status)">
            {{ statusLabel(store.current?.status) }}
          </el-tag>
          <span
            v-if="store.current?.currentVersion"
            class="version-badge"
          >
            v{{ store.current.currentVersion }}
          </span>
        </div>
        <div>
          <el-button
            type="primary"
            @click="showAiPanel = true"
          >
            <el-icon><MagicStick /></el-icon>
            {{ t('ai.panel.generateButton') }}
          </el-button>
          <el-button @click="openEditDialog">
            <el-icon><Setting /></el-icon>
            {{ t('functionUnit.settings') }}
          </el-button>
          <el-button
            :loading="exporting"
            @click="handleExport"
          >
            <el-icon><Download /></el-icon>
            {{ t('common.export') }}
          </el-button>
          <el-button
            :loading="validating"
            @click="handleValidate"
          >
            {{ t('functionUnit.validate') }}
          </el-button>
          <el-button
            type="success"
            :disabled="store.current?.status === 'PUBLISHED'"
            @click="handlePublish"
          >
            {{ t('functionUnit.publish') }}
          </el-button>
          <el-button
            type="warning"
            @click="showDeployDialog = true"
          >
            <el-icon><Upload /></el-icon>
            {{ t('functionUnit.deploy') }}
          </el-button>
        </div>
      </div>

      <el-tabs
        v-model="activeTab"
        type="border-card"
      >
        <el-tab-pane
          :label="t('functionUnit.process')"
          name="process"
        >
          <ProcessDesigner
            v-if="activeTab === 'process'"
            :function-unit-id="functionUnitId"
          />
        </el-tab-pane>
        <el-tab-pane
          :label="t('functionUnit.tables')"
          name="tables"
        >
          <TableDesigner
            v-if="activeTab === 'tables'"
            :function-unit-id="functionUnitId"
          />
        </el-tab-pane>
        <el-tab-pane
          :label="t('functionUnit.forms')"
          name="forms"
        >
          <FormDesigner
            v-if="activeTab === 'forms'"
            :function-unit-id="functionUnitId"
          />
        </el-tab-pane>
        <el-tab-pane
          :label="t('functionUnit.viewDesign')"
          name="view-design"
        >
          <MainTableViewDesignTab
            v-if="activeTab === 'view-design'"
            :function-unit-id="functionUnitId"
          />
        </el-tab-pane>
        <el-tab-pane
          :label="t('functionUnit.actionDesign')"
          name="actions"
        >
          <ActionDesigner
            v-if="activeTab === 'actions'"
            :function-unit-id="functionUnitId"
          />
        </el-tab-pane>
        <el-tab-pane
          :label="t('connection.title')"
          name="connections"
        >
          <ConnectionDesigner
            v-if="activeTab === 'connections'"
            :function-unit-id="functionUnitId"
          />
        </el-tab-pane>
        <el-tab-pane
          :label="t('emailTemplate.title')"
          name="email-templates"
        >
          <EmailTemplateDesigner
            v-if="activeTab === 'email-templates'"
            :function-unit-id="functionUnitId"
          />
        </el-tab-pane>
        <el-tab-pane
          :label="t('functionUnit.decisions')"
          name="decisions"
        >
          <DecisionList
            v-if="activeTab === 'decisions'"
            :function-unit-id="functionUnitId"
          />
        </el-tab-pane>
        <el-tab-pane
          :label="t('version.title')"
          name="versions"
        >
          <VersionManager
            v-if="activeTab === 'versions'"
            :function-unit-id="functionUnitId"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- Edit Function Unit Dialog -->
    <el-dialog
      v-model="showEditDialog"
      :title="t('functionUnit.settings')"
      width="500px"
    >
      <el-form
        :model="editForm"
        label-width="100px"
        label-position="left"
      >
        <el-form-item :label="t('functionUnit.icon')">
          <IconUploadField
            v-model="editForm.iconId"
            size="large"
          />
        </el-form-item>
        <el-form-item
          :label="t('functionUnit.name')"
          required
        >
          <el-input
            v-model="editForm.name"
            :placeholder="t('functionUnit.namePlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('functionUnit.description')">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
            :placeholder="t('functionUnit.descriptionPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('functionUnit.tags')">
          <el-select
            v-model="editForm.tags"
            multiple
            filterable
            allow-create
            default-first-option
            :placeholder="t('functionUnit.selectTags')"
            style="width: 100%;"
          >
            <el-option
              v-for="tag in availableTags"
              :key="tag"
              :label="tag"
              :value="tag"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="handleSaveEdit"
        >
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Validation Result Dialog -->
    <el-dialog
      v-model="showValidationDialog"
      :title="t('functionUnit.validationResult')"
      width="500px"
    >
      <div v-if="validationResult">
        <el-result
          v-if="validationResult.valid"
          icon="success"
          :title="t('functionUnit.validationPassed')"
          :sub-title="t('functionUnit.validationPassedDesc')"
        />
        <div v-else>
          <el-alert
            v-if="validationResult.errors?.length"
            type="error"
            :closable="false"
            style="margin-bottom: 12px;"
          >
            <template #title>
              {{ t('functionUnit.validationErrors') }} ({{ validationResult.errors.length }})
            </template>
            <ul style="margin: 8px 0 0 0; padding-left: 20px;">
              <li
                v-for="(err, i) in validationResult.errors"
                :key="i"
              >
                {{ err }}
              </li>
            </ul>
          </el-alert>
          <el-alert
            v-if="validationResult.warnings?.length"
            type="warning"
            :closable="false"
          >
            <template #title>
              {{ t('functionUnit.validationWarnings') }} ({{ validationResult.warnings.length }})
            </template>
            <ul style="margin: 8px 0 0 0; padding-left: 20px;">
              <li
                v-for="(warn, i) in validationResult.warnings"
                :key="i"
              >
                {{ warn }}
              </li>
            </ul>
          </el-alert>
        </div>
      </div>
      <template #footer>
        <el-button @click="showValidationDialog = false">
          {{ t('common.close') }}
        </el-button>
      </template>
    </el-dialog>

    <AiPanel
      :function-unit-id="functionUnitId"
      :visible="showAiPanel"
      @update:visible="showAiPanel = $event"
      @data-applied="handleAiDataApplied"
    />

    <!-- Deploy Dialog -->
    <el-dialog
      v-model="showDeployDialog"
      :title="t('functionUnit.deploy')"
      width="500px"
      @closed="cleanupDeployDialogState"
    >
      <el-form
        :model="deployForm"
        label-width="120px"
        label-position="left"
      >
        <el-form-item :label="t('functionUnit.autoEnable')">
          <el-switch v-model="deployForm.autoEnable" />
          <span style="margin-left: 12px; color: #909399; font-size: 12px;">{{ t('functionUnit.autoEnableHint') }}</span>
        </el-form-item>
        <el-form-item :label="t('functionUnit.changeLog')">
          <el-input
            v-model="deployForm.changeLog"
            type="textarea"
            :placeholder="t('functionUnit.changeLogPlaceholder')"
            :rows="3"
          />
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 16px;"
      >
        {{ t('functionUnit.deployInfo') }}
      </el-alert>
      
      <!-- Deploy Status -->
      <div
        v-if="deployStatus"
        class="deploy-status"
      >
        <el-divider>{{ t('functionUnit.deployStatusTitle') }}</el-divider>
        <div class="status-header">
          <el-tag :type="getDeployStatusType(deployStatus.status)">
            {{ getDeployStatusLabel(deployStatus.status) }}
          </el-tag>
          <span v-if="deployStatus.progress !== undefined">{{ deployStatus.progress }}%</span>
        </div>
        <el-progress 
          v-if="deployStatus.status === 'DEPLOYING'" 
          :percentage="deployStatus.progress || 0" 
        />
        <el-progress 
          v-else-if="deployStatus.status === 'SUCCESS'" 
          :percentage="100" 
          status="success"
        />
        <el-progress 
          v-else-if="deployStatus.status === 'FAILED'" 
          :percentage="deployStatus.progress || 0" 
          status="exception"
        />
        <el-alert
          v-if="deployStatus.status === 'FAILED' && deployStatus.versionNumber"
          type="warning"
          :closable="false"
          style="margin-top: 12px;"
        >
          {{ t('functionUnit.versionCreatedButDeployFailed', { version: deployStatus.versionNumber }) }}
        </el-alert>
        <div
          v-if="deployStatus.status === 'SUCCESS' && deployStatus.versionNumber"
          class="version-info"
        >
          {{ t('functionUnit.newVersion', { version: deployStatus.versionNumber }) }}
        </div>
        <div
          v-if="deployStatus.steps?.length"
          class="deploy-steps"
        >
          <div
            v-for="step in deployStatus.steps"
            :key="step.name"
            class="step-item"
          >
            <el-icon
              v-if="step.status === 'SUCCESS'"
              color="#67c23a"
            >
              <CircleCheck />
            </el-icon>
            <el-icon
              v-else-if="step.status === 'FAILED'"
              color="#f56c6c"
            >
              <CircleClose />
            </el-icon>
            <el-icon
              v-else-if="step.status === 'RUNNING'"
              color="#409eff"
            >
              <Loading />
            </el-icon>
            <el-icon
              v-else
              color="#909399"
            >
              <Clock />
            </el-icon>
            <span>{{ translateStep(step.name) }}</span>
            <span
              v-if="step.message"
              class="step-message"
            >{{ translateStep(step.message) }}</span>
          </div>
        </div>
        <div
          v-if="deployStatus.message && deployStatus.status === 'FAILED'"
          class="error-message"
        >
          {{ deployStatus.message }}
        </div>
      </div>
      
      <template #footer>
        <el-button @click="closeDeployDialog">
          {{ t('common.close') }}
        </el-button>
        <el-button 
          v-if="!deployStatus || (deployStatus.status !== 'SUCCESS' && deployStatus.status !== 'FAILED')"
          type="primary" 
          :loading="deploying" 
          :disabled="deployStatus?.status === 'DEPLOYING'"
          @click="handleDeploy"
        >
          {{ deploying ? t('functionUnit.deploying') : t('functionUnit.startDeploy') }}
        </el-button>
        <el-button 
          v-if="deployStatus?.status === 'FAILED'"
          type="primary" 
          :loading="deploying" 
          @click="handleDeploy"
        >
          {{ t('functionUnit.redeploy') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, Setting, Download, Upload, CircleCheck, CircleClose, Loading, Clock, MagicStick } from '@element-plus/icons-vue'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import ProcessDesigner from '@/components/designer/ProcessDesigner.vue'
import TableDesigner from '@/components/designer/TableDesigner.vue'
import FormDesigner from '@/components/designer/FormDesigner.vue'
import MainTableViewDesignTab from '@/components/designer/MainTableViewDesignTab.vue'
import ActionDesigner from '@/components/designer/ActionDesigner.vue'
import ConnectionDesigner from '@/components/designer/ConnectionDesigner.vue'
import EmailTemplateDesigner from '@/components/designer/EmailTemplateDesigner.vue'
import DecisionList from '@/components/designer/DecisionList.vue'
import VersionManager from '@/components/version/VersionManager.vue'
import IconPreview from '@/components/icon/IconPreview.vue'
import IconUploadField from '@/components/icon/IconUploadField.vue'
import AiPanel from '@/components/ai/AiPanel.vue'
import { useFunctionUnitStatus } from '@/composables/functionUnitEdit/useFunctionUnitStatus'
import { useFunctionUnitSettings } from '@/composables/functionUnitEdit/useFunctionUnitSettings'
import { useFunctionUnitActions } from '@/composables/functionUnitEdit/useFunctionUnitActions'
import { useFunctionUnitDeploy } from '@/composables/functionUnitEdit/useFunctionUnitDeploy'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const store = useFunctionUnitStore()

const functionUnitId = computed(() => Number(route.params.id))
const activeTab = ref('process')

watch(activeTab, (tab) => {
  if (tab === 'forms' && functionUnitId.value) {
    void store.fetchTables(functionUnitId.value)
  }
})

const showAiPanel = ref(false)

const { statusTagType, statusLabel } = useFunctionUnitStatus()

const {
  saving,
  showEditDialog,
  editForm,
  availableTags,
  openEditDialog,
  handleSaveEdit
} = useFunctionUnitSettings({ functionUnitId, store })

const {
  validating,
  exporting,
  showValidationDialog,
  validationResult,
  handleValidate,
  handlePublish,
  handleExport
} = useFunctionUnitActions({ functionUnitId, store })

const {
  deploying,
  showDeployDialog,
  deployStatus,
  deployForm,
  handleDeploy,
  stopDeployPolling,
  cleanupDeployDialogState,
  closeDeployDialog,
  getDeployStatusType,
  getDeployStatusLabel,
  translateStep
} = useFunctionUnitDeploy({ functionUnitId, store })

async function handleAiDataApplied() {
  await store.refreshAll(functionUnitId.value)
}

onMounted(() => {
  store.fetchById(functionUnitId.value)
  store.fetchAllTags()
})

onUnmounted(() => {
  stopDeployPolling()
})
</script>

<style lang="scss" scoped>
.version-badge {
  background-color: #f0f0f0;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #666;
}

.deploy-status {
  margin-top: 16px;
  
  .status-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
  }
  
  .deploy-steps {
    margin-top: 12px;
    
    .step-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 0;
      border-bottom: 1px solid #eee;
      
      &:last-child {
        border-bottom: none;
      }
      
      .step-message {
        color: #909399;
        font-size: 12px;
        margin-left: auto;
      }
    }
  }
  
  .version-info {
    margin-top: 12px;
    padding: 8px 12px;
    background-color: #f0f9eb;
    border-radius: 4px;
    color: #67c23a;
    font-size: 13px;
    font-weight: 500;
  }

  .error-message {
    margin-top: 12px;
    padding: 12px;
    background-color: #fef0f0;
    border-radius: 4px;
    color: #f56c6c;
    font-size: 13px;
  }
}
</style>
