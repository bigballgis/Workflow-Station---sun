<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('table.sla.dialogTitle')"
    width="560px"
    class="sla-config-dialog"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    @open="syncFromProps"
  >
    <p class="sla-hint">
      {{ t('table.sla.dialogHint') }}
    </p>

    <el-form
      class="sla-form"
      label-position="left"
      label-width="160px"
    >
      <el-form-item :label="t('table.sla.startSource')">
        <el-radio-group v-model="startDateSource">
          <el-radio value="FIELD">
            {{ t('table.sla.sourceField') }}
          </el-radio>
          <el-radio value="SUBMITTED_AT">
            {{ t('table.sla.sourceSubmittedAt') }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item
        v-if="startDateSource === 'FIELD'"
        :label="t('table.sla.startField')"
      >
        <el-select
          v-model="startDateField"
          :placeholder="t('table.sla.selectField')"
          :no-data-text="t('table.sla.noStartCandidates')"
          class="sla-select"
        >
          <el-option
            v-for="f in startCandidates"
            :key="f.fieldName"
            :label="`${f.displayName || f.fieldName} (${f.fieldName})`"
            :value="f.fieldName"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('table.sla.dueField')">
        <el-select
          v-model="dueDateField"
          :placeholder="t('table.sla.selectField')"
          :no-data-text="t('table.sla.noDueCandidates')"
          class="sla-select"
        >
          <el-option
            v-for="f in dueCandidates"
            :key="f.fieldName"
            :label="`${f.displayName || f.fieldName} (${f.fieldName})`"
            :value="f.fieldName"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        v-if="config"
        type="warning"
        plain
        @click="handleClear"
      >
        {{ t('table.sla.clear') }}
      </el-button>
      <el-button
        type="primary"
        :disabled="!canConfirm"
        @click="handleConfirm"
      >
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FieldDefinition, SlaConfig } from '@/api/functionUnit'
import { isTableAuditField } from '@/utils/tableAuditFields'

const props = defineProps<{
  modelValue: boolean
  fields: FieldDefinition[]
  config?: SlaConfig | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  // null clears the mapping (no due date is derived)
  'confirm': [config: SlaConfig | null]
}>()

const { t } = useI18n()

const startDateSource = ref<SlaConfig['startDateSource']>('FIELD')
const startDateField = ref<string | null>(null)
const dueDateField = ref<string | null>(null)

// Same rules as the backend SlaConfigValidator: start = DATE/TIMESTAMP, due = non-formula DATE.
const startCandidates = computed(() => props.fields.filter(
  (f) => (f.dataType === 'DATE' || f.dataType === 'TIMESTAMP') && f.fieldName !== dueDateField.value,
))
const dueCandidates = computed(() => props.fields.filter(
  (f) => f.dataType === 'DATE' && !f.isComputed && !isTableAuditField(f.fieldName)
    && f.fieldName !== startDateField.value,
))

const canConfirm = computed(() => !!dueDateField.value
  && (startDateSource.value === 'SUBMITTED_AT' || !!startDateField.value))

function exists(fieldName?: string | null): string | null {
  return fieldName && props.fields.some((f) => f.fieldName === fieldName) ? fieldName : null
}

function syncFromProps() {
  startDateSource.value = props.config?.startDateSource ?? 'FIELD'
  startDateField.value = exists(props.config?.startDateField)
  dueDateField.value = exists(props.config?.dueDateField)
}

function handleConfirm() {
  emit('confirm', {
    startDateSource: startDateSource.value,
    startDateField: startDateSource.value === 'FIELD' ? startDateField.value : null,
    dueDateField: dueDateField.value as string,
  })
  emit('update:modelValue', false)
}

function handleClear() {
  emit('confirm', null)
  emit('update:modelValue', false)
}
</script>

<style scoped>
.sla-hint {
  margin: 0 0 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.sla-form :deep(.el-form-item__label) {
  white-space: nowrap;
}

.sla-select {
  width: 100%;
}
</style>
