<template>
  <el-collapse-item
    :title="t('emailMonitor.startEvent.panelTitle')"
    name="emailMonitor"
  >
    <div v-loading="loading" class="start-email-monitor">
      <el-alert
        v-if="inboundConnections.length === 0"
        type="warning"
        :closable="false"
        :title="t('emailMonitor.noInboundConnection')"
        style="margin-bottom: 12px;"
      />

      <el-form label-position="top" size="small">
        <el-form-item :label="t('emailMonitor.startEvent.enable')">
          <el-switch
            :model-value="enabled"
            @change="onEnabledChange"
          />
          <div class="form-tip">{{ t('emailMonitor.startEvent.enableHint') }}</div>
        </el-form-item>

        <template v-if="enabled">
          <el-form-item :label="t('emailMonitor.startEvent.boundProcess')">
            <el-input :model-value="processDefinitionKey" disabled />
          </el-form-item>
          <el-form-item :label="t('emailMonitor.startEvent.boundEvent')">
            <el-input :model-value="startEventId" disabled />
          </el-form-item>
          <el-form-item :label="t('emailMonitor.name')" required>
            <el-input v-model="form.name" :placeholder="t('emailMonitor.namePlaceholder')" />
          </el-form-item>
          <el-form-item :label="t('emailMonitor.connection')" required>
            <el-select
              v-model="form.connectionUid"
              style="width: 100%;"
              :placeholder="t('emailMonitor.connectionPlaceholder')"
            >
              <el-option
                v-for="c in inboundConnections"
                :key="c.connectionUid"
                :label="`${c.name} (${c.connectionType})`"
                :value="c.connectionUid"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('emailMonitor.systemInitiator')">
            <SystemInitiatorSelect v-model="form.systemInitiatorUserId" />
          </el-form-item>
          <el-form-item :label="t('emailMonitor.filterFrom')">
            <el-input
              v-model="form.filterFrom"
              :placeholder="t('emailMonitor.filterFromPlaceholder')"
            />
          </el-form-item>
          <el-form-item :label="t('emailMonitor.filterSubject')">
            <el-input
              v-model="form.filterSubject"
              :placeholder="t('emailMonitor.filterSubjectPlaceholder')"
            />
          </el-form-item>
          <el-form-item :label="t('emailMonitor.pollInterval')">
            <el-input-number
              v-model="form.pollIntervalSeconds"
              :min="30"
              :step="30"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="form.reviewOnMissing">
              {{ t('emailMonitor.reviewOnMissing') }}
            </el-checkbox>
          </el-form-item>
          <el-form-item>
            <el-button size="small" @click="openWizard">
              {{ t('emailMonitor.startEvent.openWizard') }}
            </el-button>
            <el-tag v-if="hasExtraction" size="small" type="success" style="margin-left: 8px;">
              {{ t('emailMonitor.startEvent.extractionConfigured') }}
            </el-tag>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveMonitor">
              {{ t('emailMonitor.startEvent.save') }}
            </el-button>
          </el-form-item>
        </template>
      </el-form>
    </div>
  </el-collapse-item>

  <el-dialog
    v-if="showWizard"
    v-model="showWizard"
    :title="t('emailMonitor.wizard.title')"
    width="900px"
    top="5vh"
    append-to-body
    destroy-on-close
    :close-on-click-modal="!saving"
  >
    <EmailExtractionWizard v-model="extractionRules" :function-unit-id="functionUnitId" />
    <template #footer>
      <el-button @click="showWizard = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="confirmWizard">
        {{ t('emailMonitor.startEvent.saveWizard') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import EmailExtractionWizard from '@/components/designer/email/EmailExtractionWizard.vue'
import SystemInitiatorSelect from '@/components/designer/email/SystemInitiatorSelect.vue'
import { useStartEventEmailMonitor } from '@/composables/eventProperties/useStartEventEmailMonitor'

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
  functionUnitId: number
  updateExtProp: (name: string, value: unknown) => void
}>()

const {
  enabled,
  saving,
  loading,
  showWizard,
  inboundConnections,
  processDefinitionKey,
  startEventId,
  form,
  extractionRules,
  saveMonitor,
  onEnabledChange,
  confirmWizard,
  openWizard
} = useStartEventEmailMonitor(
  props,
  props.updateExtProp,
  t
)

const hasExtraction = computed(() => {
  const fields = extractionRules.value.fields?.length ?? 0
  const subTables = extractionRules.value.subTables?.length ?? 0
  return fields + subTables > 0
})
</script>

<style lang="scss" scoped>
.start-email-monitor {
  .form-tip {
    font-size: 11px;
    color: #909399;
    margin-top: 4px;
    line-height: 1.4;
  }
}
</style>
