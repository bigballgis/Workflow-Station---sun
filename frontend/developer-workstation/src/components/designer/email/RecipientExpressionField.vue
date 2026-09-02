<template>
  <div class="recipient-expression-field">
    <el-input
      :model-value="modelValue"
      :placeholder="placeholder"
      class="recipient-expression-field__input"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <el-popover
      v-model:visible="popoverVisible"
      :width="280"
      trigger="click"
      placement="bottom-end"
      teleported
      :z-index="popoverZIndex"
      popper-class="recipient-expression-field-popper"
      @show="onPopoverShow"
    >
      <template #reference>
        <el-button
          type="default"
          class="recipient-expression-field__insert-btn"
          :aria-label="t('properties.insertField')"
        >
          <span class="recipient-expression-field__brace" aria-hidden="true">{ }</span>
        </el-button>
      </template>
      <div class="recipient-expression-field-picker">
        <el-input
          v-model="filterQuery"
          :placeholder="t('properties.insertFieldSearch')"
          clearable
          size="small"
          class="recipient-expression-field-picker__search"
        />
        <div
          v-if="loading"
          class="recipient-expression-field-picker__status"
        >
          {{ t('common.loading') }}
        </div>
        <div
          v-else-if="filteredGroups.length === 0"
          class="recipient-expression-field-picker__status"
        >
          {{ t('properties.insertFieldEmpty') }}
        </div>
        <div
          v-else
          class="recipient-expression-field-picker__list"
        >
          <template
            v-for="group in filteredGroups"
            :key="group.label"
          >
            <div class="recipient-expression-field-picker__group-label">
              {{ groupLabel(group.label) }}
            </div>
            <button
              v-for="opt in group.options"
              :key="opt.token"
              type="button"
              class="recipient-expression-field-picker__option"
              @click="onPick(opt.token)"
            >
              <span class="recipient-expression-field-picker__option-label">{{ optionLabel(opt.label) }}</span>
              <code class="recipient-expression-field-picker__option-token">{{ opt.token }}</code>
            </button>
          </template>
        </div>
      </div>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useZIndex } from 'element-plus'
import type { EmailVariableGroup } from '@/composables/email/useEmailTemplateVariables'
import {
  appendJuelTokenToExpression,
  resolveSendTaskVariableGroupLabel,
  resolveSendTaskVariableOptionLabel,
} from '@/utils/sendTaskJuelVariables'

const props = defineProps<{
  modelValue: string
  placeholder?: string
  groups: EmailVariableGroup[]
  loading?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const { t } = useI18n()
const { nextZIndex } = useZIndex()

const popoverVisible = ref(false)
const filterQuery = ref('')
const popoverZIndex = ref(2000)

const filteredGroups = computed(() => {
  const q = filterQuery.value.trim().toLowerCase()
  const groups = props.groups
  if (!q) return groups
  return groups
    .map(group => {
      if (!group.options) {
        throw new Error(`Send task variable group "${group.label}" is missing options`)
      }
      return {
        label: group.label,
        options: group.options.filter(opt =>
          optionLabel(opt.label).toLowerCase().includes(q) || opt.token.toLowerCase().includes(q),
        ),
      }
    })
    .filter(group => group.options.length > 0)
})

function groupLabel(label: string): string {
  return resolveSendTaskVariableGroupLabel(label, t)
}

function optionLabel(label: string): string {
  return resolveSendTaskVariableOptionLabel(label, t)
}

function onPopoverShow(): void {
  filterQuery.value = ''
  popoverZIndex.value = nextZIndex()
}

function onPick(token: string): void {
  if (!token) return
  emit('update:modelValue', appendJuelTokenToExpression(props.modelValue, token))
  popoverVisible.value = false
}
</script>

<style scoped lang="scss">
.recipient-expression-field {
  display: flex;
  gap: 6px;
  align-items: stretch;
  width: 100%;

  &__input {
    flex: 1;
    min-width: 0;
  }

  &__insert-btn {
    flex-shrink: 0;
    width: 32px;
    min-width: 32px;
    padding: 0;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    font-weight: 600;
    color: #606266;
  }

  &__brace {
    line-height: 1;
    letter-spacing: -0.04em;
  }
}

.recipient-expression-field-picker {
  &__search {
    margin-bottom: 8px;
  }

  &__status {
    padding: 8px 4px;
    font-size: 12px;
    color: #909399;
    text-align: center;
  }

  &__list {
    max-height: 240px;
    overflow-y: auto;
  }

  &__group-label {
    padding: 6px 4px 4px;
    font-size: 11px;
    font-weight: 600;
    color: #909399;
    text-transform: uppercase;
    letter-spacing: 0.02em;

    &:not(:first-child) {
      margin-top: 4px;
      border-top: 1px solid #ebeef5;
      padding-top: 10px;
    }
  }

  &__option {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
    width: 100%;
    padding: 6px 8px;
    border: none;
    border-radius: 4px;
    background: transparent;
    text-align: left;
    cursor: pointer;

    &:hover,
    &:focus-visible {
      background: #f5f7fa;
      outline: none;
    }
  }

  &__option-label {
    font-size: 13px;
    color: #303133;
    line-height: 1.3;
  }

  &__option-token {
    font-size: 11px;
    color: #909399;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  }
}
</style>
