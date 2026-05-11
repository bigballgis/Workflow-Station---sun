<template>
  <el-dialog
    v-model="visible"
    :title="t('bi.rbac.createDialogTitle')"
    width="560px"
    destroy-on-close
  >
    <el-form
      ref="createFormRef"
      :model="createForm"
      :rules="createFormRules"
      label-width="140px"
    >
      <el-form-item
        :label="t('bi.rbac.colSystemRole')"
        prop="sysRoleId"
      >
        <el-select
          v-model="createForm.sysRoleId"
          filterable
          :placeholder="t('bi.rbac.selectSystemRole')"
          :loading="unmappedRolesLoading"
          style="width: 100%"
        >
          <el-option
            v-for="role in unmappedRoles"
            :key="role.id"
            :label="`${role.name} (${role.code})`"
            :value="role.id"
          />
        </el-select>
        <el-empty
          v-if="!unmappedRolesLoading && unmappedRoles.length === 0"
          :description="t('bi.rbac.noUnmappedRoles')"
          :image-size="60"
        />
      </el-form-item>
      <el-form-item
        :label="t('bi.rbac.colSupersetRoles')"
        prop="supersetRoleIds"
      >
        <el-select
          v-model="createForm.supersetRoleIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          :placeholder="t('bi.rbac.selectSupersetRoles')"
          :loading="createSupersetRolesLoading"
          style="width: 100%"
        >
          <el-option
            v-for="role in createActiveSupersetRoles"
            :key="role.supersetRoleId"
            :label="role.name"
            :value="role.supersetRoleId"
          />
        </el-select>
        <el-empty
          v-if="!createSupersetRolesLoading && createActiveSupersetRoles.length === 0"
          :description="t('bi.rbac.noSupersetRoles')"
          :image-size="60"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">
        {{ t('bi.rbac.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="createLoading"
        @click="emit('submit')"
      >
        {{ t('bi.rbac.ok') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
  createForm: { sysRoleId: string; supersetRoleIds: number[] }
  createFormRules: FormRules
  unmappedRoles: { id: string; name: string; code: string }[]
  unmappedRolesLoading: boolean
  createActiveSupersetRoles: { supersetRoleId: number; name: string }[]
  createSupersetRolesLoading: boolean
  createLoading: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'submit': []
}>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { emit('update:modelValue', v) })

const createFormRef = ref<FormInstance>()
defineExpose({ createFormRef })
</script>
