<template>
  <el-dialog
    :model-value="showActionColumnConfig"
    :title="editingActionColumnType === 'lookup' ? 'Lookup' : $t('linkForm.componentName')"
    width="420px"
    destroy-on-close
    @update:model-value="$emit('update:showActionColumnConfig', $event)"
  >
    <el-form
      v-if="editingActionColumnType === 'linkForm'"
      :model="linkColumnConfig"
      label-width="auto"
      label-position="left"
    >
      <el-form-item :label="$t('linkForm.boundSubTable')">
        <el-select
          v-model="linkColumnConfig.boundSubTableBindingId"
          :placeholder="$t('linkForm.selectSubTable')"
          filterable
          style="width: 100%"
        >
          <el-option
            v-for="subTable in effectiveSubTableOptions"
            :key="subTable.bindingId"
            :label="subTable.tableDisplayName || subTable.tableName"
            :value="subTable.bindingId"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('linkForm.columnLabel')">
        <el-input
          v-model="linkColumnConfig.columnLabel"
          :placeholder="$t('linkForm.columnLabelPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="$t('linkForm.linkText')">
        <el-input
          v-model="linkColumnConfig.linkText"
          :placeholder="$t('linkForm.linkTextPlaceholder')"
        />
      </el-form-item>
    </el-form>
    <el-form
      v-else
      :model="lookupColumnConfig"
      label-width="auto"
      label-position="left"
    >
      <el-form-item :label="$t('linkForm.columnLabel')">
        <el-input
          v-model="lookupColumnConfig.columnLabel"
          :placeholder="$t('linkForm.columnLabelPlaceholder')"
        />
      </el-form-item>
      <el-form-item label="Lookup Config">
        <LookupBindingSelect v-model="lookupColumnConfig.lookupConfig" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:showActionColumnConfig', false)">
        {{ $t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        @click="$emit('save')"
      >
        {{ $t('common.save') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import LookupBindingSelect from '../LookupBindingSelect.vue'

const props = defineProps<{
  showActionColumnConfig: boolean
  editingActionColumnType: string
  linkColumnConfig: any
  lookupColumnConfig: any
  subTableBindingOptions: any[]
}>()

// The bound binding may belong to ANOTHER form of this unit (e.g. the MI demo binds the Main
// form's Participants link column to the Sub task form's People binding). It is absent from the
// current form's options, so append it with the column's persisted table name — otherwise the
// select renders the raw binding id.
const effectiveSubTableOptions = computed(() => {
  const options = props.subTableBindingOptions || []
  const bound = props.linkColumnConfig?.boundSubTableBindingId
  if (bound == null || bound === 0 || options.some((o: any) => o.bindingId === bound)) {
    return options
  }
  return [
    ...options,
    { bindingId: bound, tableName: props.linkColumnConfig?.boundSubTableName || String(bound) },
  ]
})

defineEmits<{
  'update:showActionColumnConfig': [value: boolean]
  save: []
}>()
</script>
