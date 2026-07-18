<template>
  <div class="record-note-scope-select">
    <el-select
      :model-value="value"
      style="width: 100%"
      @update:model-value="onChange"
    >
      <el-option
        label="Whole table"
        value="TABLE"
      />
      <el-option
        label="Single record"
        value="RECORD"
        :disabled="!isSubForm"
      />
    </el-select>
    <div
      v-if="!isSubForm"
      class="rn-scope-hint"
    >
      {{ t('form.recordNoteScopeMainHint') }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

// Scope rule: Single Record is only selectable when the component sits on a
// sub-table form; the main canvas is whole-table only. (Relation Table tabs
// have no form-design canvas, so the component can never land there.)
// Host detection is DOM-based — sub-form designers are mounted inside
// `.sub-table-design-wrapper`, the main canvas is not — because fc-designer
// instantiates panel widgets in a separate app context (no provide/inject).
const props = defineProps<{ modelValue?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const { t } = useI18n()

const isSubForm = ref(false)
const value = computed(() => (props.modelValue === 'RECORD' ? 'RECORD' : 'TABLE'))

onMounted(() => {
  const el = getCurrentInstance()?.proxy?.$el as HTMLElement | null
  isSubForm.value = !!el?.closest('.sub-table-design-wrapper')
  // Normalize legacy configs: RECORD saved on a non-sub host coerces to TABLE.
  if (!isSubForm.value && props.modelValue === 'RECORD') {
    emit('update:modelValue', 'TABLE')
  }
})

function onChange(next: string) {
  emit('update:modelValue', next === 'RECORD' && isSubForm.value ? 'RECORD' : 'TABLE')
}
</script>

<style scoped>
/* Sub-form designer panels wrap custom fields in a shrink-to-fit container,
   collapsing width:100% children to ~30px — pin a usable minimum. */
.record-note-scope-select {
  width: 100%;
  min-width: 180px;
}

.rn-scope-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: #909399;
}
</style>
