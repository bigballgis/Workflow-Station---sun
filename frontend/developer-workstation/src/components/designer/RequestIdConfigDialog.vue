<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('table.requestId.dialogTitle')"
    width="640px"
    top="8vh"
    class="request-id-config-dialog"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    @open="syncFromProps"
  >
    <p class="request-id-hint">
      {{ t('table.requestId.dialogHint') }}
    </p>

    <div class="request-id-grid">
      <!-- 左:可选字段 -->
      <div class="request-id-pane">
        <div class="pane-title">
          {{ t('table.requestId.availableFields') }}
        </div>
        <div
          v-if="!fields.length"
          class="pane-empty"
        >
          {{ t('table.requestId.noFields') }}
        </div>
        <el-checkbox-group
          v-else
          v-model="selectedFieldNames"
          class="field-checkbox-group"
        >
          <el-checkbox
            v-for="f in fields"
            :key="f.fieldName"
            :value="f.fieldName"
            class="field-checkbox"
          >
            <span class="field-label">{{ f.displayName || f.fieldName }}</span>
            <span class="field-code">{{ f.fieldName }}</span>
          </el-checkbox>
        </el-checkbox-group>
      </div>

      <!-- 右:已选字段 + 排序 -->
      <div class="request-id-pane">
        <div class="pane-title">
          {{ t('table.requestId.selectedOrder') }}
        </div>
        <div
          v-if="!orderedSelected.length"
          class="pane-empty"
        >
          {{ t('table.requestId.selectHint') }}
        </div>
        <ul
          v-else
          class="ordered-list"
        >
          <li
            v-for="(name, index) in orderedSelected"
            :key="name"
            class="ordered-item"
          >
            <span class="ordered-index">{{ index + 1 }}</span>
            <span class="ordered-label">{{ fieldLabel(name) }}</span>
            <span class="ordered-btns">
              <el-button
                link
                size="small"
                :disabled="index === 0"
                @click="moveUp(index)"
              >
                <el-icon><CaretTop /></el-icon>
              </el-button>
              <el-button
                link
                size="small"
                :disabled="index === orderedSelected.length - 1"
                @click="moveDown(index)"
              >
                <el-icon><CaretBottom /></el-icon>
              </el-button>
            </span>
          </li>
        </ul>
      </div>
    </div>

    <div class="request-id-footer-config">
      <div class="separator-field">
        <label class="separator-label">{{ t('table.requestId.separator') }}</label>
        <el-select
          v-model="separator"
          size="small"
          class="separator-select"
        >
          <el-option
            v-for="opt in separatorOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </div>
      <div class="preview-field">
        <label class="preview-label">{{ t('table.requestId.preview') }}</label>
        <code class="preview-value">{{ previewText }}</code>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        v-if="orderedSelected.length"
        type="warning"
        plain
        @click="handleClear"
      >
        {{ t('table.requestId.clear') }}
      </el-button>
      <el-button
        type="primary"
        @click="handleConfirm"
      >
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { CaretTop, CaretBottom } from '@element-plus/icons-vue'
import type { FieldDefinition, RequestIdConfig } from '@/api/functionUnit'

const props = defineProps<{
  modelValue: boolean
  fields: FieldDefinition[]
  config?: RequestIdConfig | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  // null 表示清空配置(回到缺省)
  'confirm': [config: RequestIdConfig | null]
}>()

const { t } = useI18n()

const selectedFieldNames = ref<string[]>([])
const separator = ref<string>('-')

const separatorOptions = computed(() => [
  { value: '-', label: t('table.requestId.sep.dash') + ' ( - )' },
  { value: '/', label: t('table.requestId.sep.slash') + ' ( / )' },
  { value: '_', label: t('table.requestId.sep.underscore') + ' ( _ )' },
  { value: '.', label: t('table.requestId.sep.dot') + ' ( . )' },
  { value: ' ', label: t('table.requestId.sep.space') },
  { value: '', label: t('table.requestId.sep.none') },
])

// 已选字段按用户定义的顺序展示;勾选/取消时维护这个有序数组
const orderedSelected = ref<string[]>([])

// el-checkbox-group 只管"集合",我们用一个 computed 把它桥接到有序数组:
// 勾选新增 → append 到末尾;取消 → 从有序数组移除。
const selectedSet = computed(() => new Set(selectedFieldNames.value))

function reconcileOrder() {
  // 移除已取消的
  orderedSelected.value = orderedSelected.value.filter((n) => selectedSet.value.has(n))
  // 追加新勾选的(保持字段定义顺序作为初始追加序)
  for (const f of props.fields) {
    if (selectedSet.value.has(f.fieldName) && !orderedSelected.value.includes(f.fieldName)) {
      orderedSelected.value.push(f.fieldName)
    }
  }
}

// 监听复选变化 → 同步有序数组
watch(selectedFieldNames, reconcileOrder, { deep: true })

function fieldLabel(fieldName: string): string {
  const f = props.fields.find((x) => x.fieldName === fieldName)
  return f?.displayName || fieldName
}

function moveUp(index: number) {
  if (index <= 0) return
  const arr = orderedSelected.value
  ;[arr[index - 1], arr[index]] = [arr[index], arr[index - 1]]
}

function moveDown(index: number) {
  const arr = orderedSelected.value
  if (index >= arr.length - 1) return
  ;[arr[index + 1], arr[index]] = [arr[index], arr[index + 1]]
}

const previewText = computed(() => {
  if (!orderedSelected.value.length) return t('table.requestId.previewEmpty')
  return orderedSelected.value.map((n) => `[${fieldLabel(n)}]`).join(separator.value)
})

function syncFromProps() {
  const cfg = props.config
  if (cfg && Array.isArray(cfg.fieldNames) && cfg.fieldNames.length) {
    // 只保留仍存在于当前字段集合里的 fieldName(字段可能已被删除)
    const valid = cfg.fieldNames.filter((n) => props.fields.some((f) => f.fieldName === n))
    orderedSelected.value = [...valid]
    selectedFieldNames.value = [...valid]
    separator.value = cfg.separator ?? '-'
  } else {
    orderedSelected.value = []
    selectedFieldNames.value = []
    separator.value = '-'
  }
}

function handleConfirm() {
  if (!orderedSelected.value.length) {
    emit('confirm', null)
  } else {
    emit('confirm', {
      fieldNames: [...orderedSelected.value],
      separator: separator.value,
    })
  }
  emit('update:modelValue', false)
}

function handleClear() {
  orderedSelected.value = []
  selectedFieldNames.value = []
  emit('confirm', null)
  emit('update:modelValue', false)
}
</script>

<style scoped>
.request-id-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.request-id-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.request-id-pane {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 10px 12px;
  min-height: 200px;
  max-height: 320px;
  overflow-y: auto;
}

.pane-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
}

.pane-empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  padding: 8px 0;
}

.field-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.field-checkbox {
  display: flex;
  align-items: center;
  height: auto;
  margin-right: 0;
}

.field-label {
  font-size: 13px;
}

.field-code {
  margin-left: 6px;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  font-family: var(--el-font-family-mono, monospace);
}

.ordered-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.ordered-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.ordered-index {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ordered-label {
  flex: 1;
  font-size: 13px;
}

.ordered-btns {
  display: inline-flex;
}

.request-id-footer-config {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  flex-wrap: wrap;
}

.separator-field,
.preview-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.separator-label,
.preview-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.separator-select {
  width: 160px;
}

.preview-value {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 13px;
  background: var(--el-fill-color-light);
  padding: 2px 8px;
  border-radius: 4px;
  color: var(--el-color-primary);
}
</style>
