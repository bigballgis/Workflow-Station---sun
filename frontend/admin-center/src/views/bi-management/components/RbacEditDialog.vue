<template>
  <el-dialog
    v-model="visible"
    :title="t('bi.rbac.editDialogTitle')"
    width="560px"
    destroy-on-close
  >
    <el-form label-width="140px">
      <el-form-item :label="t('bi.rbac.colSystemRole')">
        <span>{{ editForm.sysRoleName }}</span>
      </el-form-item>
      <el-form-item :label="t('bi.rbac.colSupersetRoles')">
        <el-select
          v-model="editForm.selectedRoleIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          :placeholder="t('bi.rbac.selectSupersetRoles')"
          :loading="supersetRolesLoading"
          style="width: 100%"
        >
          <el-option
            v-for="role in activeSupersetRoles"
            :key="role.supersetRoleId"
            :label="role.name"
            :value="role.supersetRoleId"
          />
        </el-select>
        <el-empty
          v-if="!supersetRolesLoading && activeSupersetRoles.length === 0"
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
        :loading="editLoading"
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

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
  editForm: { sysRoleId: string; sysRoleName: string; selectedRoleIds: number[] }
  activeSupersetRoles: { supersetRoleId: number; name: string }[]
  supersetRolesLoading: boolean
  editLoading: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'submit': []
}>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { emit('update:modelValue', v) })
</script>
