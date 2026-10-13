<template>
  <el-form-item>
    <template #label>
      <span class="allowed-roles-label">
        {{ t('action.allowedRoles') }}
        <DesignerHelpLink
          path="/action-allowed-roles"
          :aria-label="t('action.allowedRolesGuideLinkAria')"
          test-id="action-allowed-roles-guide-link"
        />
      </span>
    </template>
    <el-select
      :model-value="modelValue"
      multiple
      filterable
      :loading="loadingRoles"
      :placeholder="t('action.allowedRolesPlaceholder')"
      style="width: 100%"
      @update:model-value="onChange"
    >
      <el-option
        v-for="role in roleOptions"
        :key="role.code"
        :label="role.name"
        :value="role.code"
      />
    </el-select>
  </el-form-item>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import DesignerHelpLink from '@/components/designer/DesignerHelpLink.vue'
import { useActionRoleCatalog } from '@/composables/actionDesigner/useActionRoleCatalog'

const props = defineProps<{ modelValue: string[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()

const { t } = useI18n()
const { roleOptions, loadingRoles } = useActionRoleCatalog(() => props.modelValue ?? [])

function onChange(value: string[]): void {
  emit('update:modelValue', value)
}
</script>

<style scoped>
.allowed-roles-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
