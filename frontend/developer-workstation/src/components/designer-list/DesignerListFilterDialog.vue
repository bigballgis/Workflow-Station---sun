<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  operatorNeedsValue,
  type GridColumnFilter,
  type GridFilterOperator,
} from '@/utils/designerListGridRuntime'

const props = defineProps<{
  visible: boolean
  label: string
  filter: GridColumnFilter | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  apply: [filter: GridColumnFilter]
  clear: []
}>()

const { t } = useI18n()

const draft = ref<GridColumnFilter>({ operator: 'contains', value: '' })

const dialogVisible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v),
})

const needsValue = computed(() => operatorNeedsValue(draft.value.operator))

const operatorOptions: Array<{ value: GridFilterOperator; labelKey: string }> = [
  { value: 'contains', labelKey: 'designerList.opContains' },
  { value: 'eq', labelKey: 'designerList.opEquals' },
  { value: 'ne', labelKey: 'designerList.opNotEquals' },
  { value: 'startsWith', labelKey: 'designerList.opStartsWith' },
  { value: 'endsWith', labelKey: 'designerList.opEndsWith' },
  { value: 'notContains', labelKey: 'designerList.opNotContains' },
  { value: 'isNotNull', labelKey: 'designerList.opHasData' },
  { value: 'isNull', labelKey: 'designerList.opNoData' },
]

watch(
  () => props.visible,
  (open) => {
    if (open) {
      draft.value = {
        operator: props.filter?.operator ?? 'contains',
        value: props.filter?.value ?? '',
      }
    }
  },
)

function onApply() {
  emit('apply', { ...draft.value })
}

function onClear() {
  emit('clear')
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="label ? `${t('designerList.filter')}: ${label}` : t('designerList.filter')"
    width="420px"
    append-to-body
    destroy-on-close
  >
    <el-form label-position="top">
      <el-form-item :label="t('designerList.filterOperator')">
        <el-select
          v-model="draft.operator"
          style="width: 100%;"
        >
          <el-option
            v-for="op in operatorOptions"
            :key="op.value"
            :label="t(op.labelKey)"
            :value="op.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="needsValue"
        :label="t('designerList.filterValue')"
      >
        <el-input
          v-model="draft.value"
          :placeholder="t('designerList.filterValuePlaceholder')"
          clearable
          @keyup.enter="onApply"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="onClear">{{ t('designerList.clear') }}</el-button>
      <el-button
        type="primary"
        @click="onApply"
      >
        {{ t('designerList.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>
