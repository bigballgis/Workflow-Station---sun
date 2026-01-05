<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="`${t('role.configPermission')} - ${role?.name}`" width="800px">
    <el-tree
      ref="treeRef"
      :data="roleStore.permissions"
      :props="{ label: 'name', children: 'children' }"
      show-checkbox
      node-key="id"
      default-expand-all
      :default-checked-keys="checkedKeys"
    >
      <template #default="{ data }">
        <span class="permission-node">
          <span>{{ data.name }} ({{ data.code }})</span>
          <span v-if="data.actions?.length" class="actions">
            <el-checkbox-group v-model="actionMap[data.id]" size="small">
              <el-checkbox v-for="action in data.actions" :key="action" :label="action">{{ actionText(action) }}</el-checkbox>
            </el-checkbox-group>
          </span>
        </span>
      </template>
    </el-tree>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="loading" @click="handleSave">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useRoleStore } from '@/stores/role'
import { Role, roleApi } from '@/api/role'

const props = defineProps<{ modelValue: boolean; role: Role | null }>()
const emit = defineEmits(['update:modelValue'])

const { t } = useI18n()
const roleStore = useRoleStore()

const treeRef = ref()
const loading = ref(false)
const checkedKeys = ref<string[]>([])
const actionMap = reactive<Record<string, string[]>>({})

const actionText = (action: string) => ({ CREATE: t('permission.create'), READ: t('permission.read'), UPDATE: t('permission.update'), DELETE: t('permission.delete'), EXECUTE: t('permission.execute') }[action] || action)

watch(() => props.modelValue, async (val) => {
  if (val && props.role) {
    await roleStore.fetchPermissionTree()
    const permissions = await roleApi.getPermissions(props.role.id)
    checkedKeys.value = permissions.map(p => p.permissionId)
    permissions.forEach(p => { actionMap[p.permissionId] = p.actions })
  }
})

const handleSave = async () => {
  if (!props.role) return
  loading.value = true
  try {
    const checkedNodes = treeRef.value.getCheckedKeys()
    const permissions = checkedNodes.map((id: string) => ({
      roleId: props.role!.id,
      permissionId: id,
      actions: actionMap[id] || ['READ']
    }))
    await roleStore.updateRolePermissions(props.role.id, permissions)
    ElMessage.success(t('common.success'))
    emit('update:modelValue', false)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.permission-node {
  display: flex;
  align-items: center;
  gap: 20px;
  
  .actions {
    .el-checkbox-group {
      display: inline-flex;
    }
  }
}
</style>
