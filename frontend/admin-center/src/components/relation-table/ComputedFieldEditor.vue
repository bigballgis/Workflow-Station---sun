<!--
  Relation Table counterpart of
  frontend/developer-workstation/src/components/designer/ComputedFieldEditor.vue.
  Kept as a separate component for the same reason PkGenerationEditor and FieldForeignKeyEditor are:
  the two apps own their own designer surfaces. Shared parse/serialize lives in
  frontend/shared/src/computedFieldConfig.ts (re-exported via @/utils/computedFieldConfig).

  One deliberate difference: a Relation Table has no sub-tables, so there is no scope choice here.
  Every formula is row scope, and the backend rejects aggregates on relation tables.
-->
<template>
  <div class="computed-field-editor">
    <el-switch
      v-model="enabled"
      size="small"
      :disabled="disabled"
      @change="onToggle"
    />
    <el-tooltip
      v-if="enabled"
      :content="summaryLabel"
      placement="top"
      :show-after="400"
    >
      <el-button
        size="small"
        circle
        class="computed-config-btn"
        :type="isConfigured ? 'primary' : 'warning'"
        plain
        @click="openDialog"
      >
        <el-icon><EditPen /></el-icon>
      </el-button>
    </el-tooltip>

    <el-dialog
      v-model="dialogVisible"
      :title="t('relationTable.computedField.dialogTitle')"
      width="640px"
      top="8vh"
      class="computed-field-dialog"
      append-to-body
      destroy-on-close
      @open="syncFromProps"
    >
      <p class="computed-lede">
        {{ t('relationTable.computedField.dialogHint') }}
      </p>

      <section class="cf-block">
        <h3 class="cf-heading">
          {{ t('relationTable.computedField.formula') }}
        </h3>
        <el-input
          v-model="localSource"
          type="textarea"
          :rows="5"
          class="formula-input"
          :placeholder="t('relationTable.computedField.formulaPlaceholder')"
          spellcheck="false"
        />
        <p
          v-if="parseMessage"
          class="parse-error"
        >
          {{ parseMessage }}
        </p>
      </section>

      <section class="cf-block">
        <h3 class="cf-heading">
          {{ t('relationTable.computedField.onError') }}
        </h3>
        <div
          class="choice-row"
          role="radiogroup"
        >
          <button
            type="button"
            class="choice"
            :class="{ on: localOnError === 'fail' }"
            @click="localOnError = 'fail'"
          >
            <span class="choice-title">{{ t('relationTable.computedField.onErrorFail') }}</span>
            <span class="choice-hint">{{ t('relationTable.computedField.onErrorFailHint') }}</span>
          </button>
          <button
            type="button"
            class="choice"
            :class="{ on: localOnError === 'null' }"
            @click="localOnError = 'null'"
          >
            <span class="choice-title">{{ t('relationTable.computedField.onErrorNull') }}</span>
            <span class="choice-hint">{{ t('relationTable.computedField.onErrorNullHint') }}</span>
          </button>
        </div>
      </section>

      <section class="cf-block cf-preview">
        <h3 class="cf-heading">
          {{ t('relationTable.computedField.preview') }}
        </h3>
        <p
          v-if="!previewDeps.length"
          class="preview-empty"
        >
          {{ t('relationTable.computedField.previewEmpty') }}
        </p>
        <div
          v-else
          class="preview-deps"
        >
          <label
            v-for="dep in previewDeps"
            :key="dep"
            class="preview-dep-row"
          >
            <span class="preview-dep-name">{{ dep }}</span>
            <el-input
              v-model="sampleRow[dep]"
              size="small"
              @input="maybeAutoPreview"
            />
          </label>
        </div>
        <div class="preview-actions">
          <el-button
            size="small"
            :disabled="!localSource.trim()"
            @click="runPreview"
          >
            {{ t('relationTable.computedField.tryEvaluate') }}
          </el-button>
          <span
            v-if="previewResult"
            class="preview-result"
          >
            <span class="preview-result-label">{{ t('relationTable.computedField.previewResultLabel') }}</span>
            {{ previewResult }}
          </span>
        </div>
      </section>

      <template #footer>
        <el-button @click="dialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          @click="confirmDialog"
        >
          {{ t('relationTable.computedField.applyFormula') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { EditPen } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  buildComputedFieldDefinition,
  computedFieldSummary,
  parseComputedFieldFromApi,
  type ComputedFieldDefinition,
  type ComputedFieldOnError,
} from '@/utils/computedFieldConfig'
import { evaluateAst, toText, type EvaluationContext } from '@platform-shared/computedField'

const props = defineProps<{
  isComputed?: boolean
  computedField?: Record<string, unknown> | ComputedFieldDefinition | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:isComputed': [value: boolean]
  'update:computedField': [value: ComputedFieldDefinition | undefined]
}>()

const { t } = useI18n()

const enabled = ref(!!props.isComputed)
const dialogVisible = ref(false)
const localSource = ref('')
const localOnError = ref<ComputedFieldOnError>('fail')
const parseMessage = ref('')
const previewResult = ref('')
const previewDeps = ref<string[]>([])
const sampleRow = reactive<Record<string, string>>({})

const currentDefinition = computed(() => parseComputedFieldFromApi(props.computedField ?? undefined))

const isConfigured = computed(() => !!currentDefinition.value?.source?.trim())

const summaryLabel = computed(() => {
  const summary = computedFieldSummary(currentDefinition.value)
  return summary || t('relationTable.computedField.configure')
})

watch(
  () => props.isComputed,
  (v) => { enabled.value = !!v },
)

watch(localSource, () => {
  refreshDepsFromSource()
})

function syncFromProps(): void {
  const def = currentDefinition.value
  localSource.value = def?.source ?? ''
  localOnError.value = def?.onError ?? 'fail'
  parseMessage.value = ''
  previewResult.value = ''
  Object.keys(sampleRow).forEach((k) => delete sampleRow[k])
  refreshDepsFromSource()
}

function onToggle(val: boolean | string | number): void {
  const on = val === true
  emit('update:isComputed', on)
  if (!on) {
    emit('update:computedField', undefined)
    parseMessage.value = ''
    previewResult.value = ''
    return
  }
  if (!isConfigured.value) {
    openDialog()
  }
}

function openDialog(): void {
  dialogVisible.value = true
}

function refreshDepsFromSource(): void {
  const source = localSource.value.trim()
  if (!source) {
    previewDeps.value = []
    parseMessage.value = ''
    previewResult.value = ''
    return
  }
  const built = buildComputedFieldDefinition(source, 'row', localOnError.value)
  if (!built.ok) {
    return
  }
  parseMessage.value = ''
  const next = built.value.dependsOn.filter((d) => !d.includes('.'))
  previewDeps.value = next
  for (const key of Object.keys(sampleRow)) {
    if (!next.includes(key)) delete sampleRow[key]
  }
  for (const dep of next) {
    if (!(dep in sampleRow)) sampleRow[dep] = ''
  }
  maybeAutoPreview()
}

function maybeAutoPreview(): void {
  if (!previewDeps.value.length) return
  if (previewDeps.value.some((dep) => !String(sampleRow[dep] ?? '').trim())) {
    previewResult.value = ''
    return
  }
  runPreview()
}

function formatOutcome(outcome: ReturnType<typeof evaluateAst>): string {
  if (!outcome.ok) return `#ERR ${outcome.error.code}: ${outcome.error.message}`
  if (outcome.value.kind === 'blank') return t('relationTable.computedField.previewBlank')
  return toText(outcome.value)
}

function runPreview(): void {
  const built = buildComputedFieldDefinition(localSource.value, 'row', localOnError.value)
  if (!built.ok) {
    parseMessage.value = built.position != null
      ? `${built.message} (@${built.position})`
      : built.message
    previewResult.value = ''
    return
  }
  parseMessage.value = ''
  const row: Record<string, unknown> = {}
  for (const [key, val] of Object.entries(sampleRow)) {
    row[key] = val
  }
  const ctx: EvaluationContext = { row, subTables: {} }
  previewResult.value = formatOutcome(evaluateAst(built.value.ast, ctx))
}

function confirmDialog(): void {
  const built = buildComputedFieldDefinition(localSource.value, 'row', localOnError.value)
  if (!built.ok) {
    parseMessage.value = built.position != null
      ? `${built.message} (@${built.position})`
      : built.message
    ElMessage.warning(t('relationTable.computedField.invalidFormula'))
    return
  }
  emit('update:isComputed', true)
  emit('update:computedField', built.value)
  dialogVisible.value = false
  ElMessage.success(t('relationTable.computedField.savedLocally'))
}
</script>

<style scoped>
.computed-field-editor {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.computed-config-btn {
  width: 24px;
  height: 24px;
  padding: 0;
}
.computed-lede {
  margin: 0 0 18px;
  font-size: 13px;
  line-height: 1.45;
  color: var(--ws-text-secondary);
}
.cf-block {
  margin-bottom: 18px;
}
.cf-heading {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ws-text-muted);
}
.formula-input :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 14px;
  line-height: 1.55;
  background: var(--ws-canvas);
}
.parse-error {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-color-danger);
}
.choice-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.choice {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 10px 12px;
  text-align: left;
  font: inherit;
  border: 1px solid var(--ws-line-strong);
  border-radius: var(--ws-radius-input);
  background: var(--ws-card-bg);
  color: var(--ws-text);
  cursor: pointer;
}
.choice:hover {
  border-color: var(--ws-ink);
}
.choice.on {
  border-color: var(--ws-ink);
  box-shadow: inset 3px 0 0 var(--primary-color);
  background: var(--primary-soft);
}
.choice-title {
  font-size: 13px;
  font-weight: 600;
}
.choice-hint {
  font-size: 12px;
  line-height: 1.4;
  color: var(--ws-text-secondary);
}
.preview-empty {
  margin: 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--ws-text-secondary);
}
.cf-preview {
  padding: 12px;
  border-radius: var(--ws-radius-input);
  background: var(--ws-canvas);
}
.preview-deps {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.preview-dep-row {
  display: grid;
  grid-template-columns: minmax(96px, 140px) 1fr;
  gap: 8px;
  align-items: center;
}
.preview-dep-name {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--ws-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preview-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.preview-result {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  color: var(--ws-ink);
}
.preview-result-label {
  margin-right: 6px;
  font-family: inherit;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ws-text-muted);
}
</style>
