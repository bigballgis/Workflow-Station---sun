<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import MainTableViewFilterGroup from './MainTableViewFilterGroup.vue'
import {
  type FilterConfig,
  type FilterFieldOption,
  type FilterGroupEditorNode,
  parseFilterConfigToEditorRoot,
  serializeFilterEditorRoot,
} from '@/utils/mainTableViewFilter'

const props = defineProps<{
  modelValue: boolean
  filterConfig?: FilterConfig | null
  fieldOptions: FilterFieldOption[]
}>()

const emit = defineEmits<{
  'update:modelValue': [visible: boolean]
  save: [config: FilterConfig]
}>()

const { t } = useI18n()

const rootGroup = ref<FilterGroupEditorNode>(parseFilterConfigToEditorRoot(null))

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    rootGroup.value = parseFilterConfigToEditorRoot(props.filterConfig)
  },
)

function closeDialog() {
  emit('update:modelValue', false)
}

function confirmDialog() {
  emit('save', serializeFilterEditorRoot(rootGroup.value))
  closeDialog()
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('mainTableView.editFiltersTitle')"
    width="760px"
    destroy-on-close
    class="mtv-filter-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="filter-editor-root">
      <MainTableViewFilterGroup
        :group="rootGroup"
        :depth="0"
        :field-options="fieldOptions"
      />
    </div>

    <template #footer>
      <el-button @click="closeDialog">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        @click="confirmDialog"
      >
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.filter-editor-root {
  max-height: 60vh;
  overflow-y: auto;
  padding: 4px 0;
}
</style>
