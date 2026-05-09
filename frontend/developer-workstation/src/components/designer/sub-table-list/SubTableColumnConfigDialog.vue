<template>
  <el-dialog
    :model-value="showActionColumnConfig"
    @update:model-value="$emit('update:showActionColumnConfig', $event)"
    :title="editingActionColumnType === 'lookup' ? 'Lookup' : $t('linkForm.componentName')"
    width="420px"
    destroy-on-close
  >
    <el-form v-if="editingActionColumnType === 'linkForm'" :model="linkColumnConfig" label-width="120px" label-position="left">
      <el-form-item :label="$t('linkForm.boundSubTable')">
        <el-select
          v-model="linkColumnConfig.boundSubTableBindingId"
          :placeholder="$t('linkForm.selectSubTable')"
          filterable
          style="width: 100%"
        >
          <el-option
            v-for="subTable in subTableBindingOptions"
            :key="subTable.bindingId"
            :label="subTable.tableName"
            :value="subTable.bindingId"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('linkForm.columnLabel')">
        <el-input v-model="linkColumnConfig.columnLabel" :placeholder="$t('linkForm.columnLabelPlaceholder')" />
      </el-form-item>
      <el-form-item :label="$t('linkForm.linkText')">
        <el-input v-model="linkColumnConfig.linkText" :placeholder="$t('linkForm.linkTextPlaceholder')" />
      </el-form-item>
    </el-form>
    <el-form v-else :model="lookupColumnConfig" label-width="120px" label-position="left">
      <el-form-item :label="$t('linkForm.columnLabel')">
        <el-input v-model="lookupColumnConfig.columnLabel" :placeholder="$t('linkForm.columnLabelPlaceholder')" />
      </el-form-item>
      <el-form-item label="Lookup Config">
        <LookupBindingSelect v-model="lookupColumnConfig.lookupConfig" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:showActionColumnConfig', false)">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" @click="$emit('save')">{{ $t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import LookupBindingSelect from '../LookupBindingSelect.vue'

defineProps<{
  showActionColumnConfig: boolean
  editingActionColumnType: string
  linkColumnConfig: any
  lookupColumnConfig: any
  subTableBindingOptions: any[]
}>()

defineEmits<{
  'update:showActionColumnConfig': [value: boolean]
  save: []
}>()
</script>
