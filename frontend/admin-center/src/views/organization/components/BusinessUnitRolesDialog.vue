<template>
  <el-dialog
    v-model="visible"
    :title="t('organization.eligibleRoles') + ' - ' + (businessUnit?.name || '')"
    width="600px"
    @open="fetchRoles"
  >
    <div class="hint-text">{{ t('organization.eligibleRolesDesc') }}</div>
    <div class="roles-header">
      <el-select v-model="selectedRoleId" :placeholder="t('common.selectPlaceholder')" filterable style="width: 300px">
        <el-option
          v-for="role in availableRoles"
          :key="role.id"
          :label="role.name"
          :value="role.id"
        />
      </el-select>
      <el-button type="primary" @click="handleBindRole" :disabled="!selectedRoleId">
        {{ t('common.add') }}
      </el-button>
    </div>

    <el-table :data="boundRoles" v-loading="loading" stripe style="margin-top: 16px">
      <el-table-column prop="name" :label="t('role.roleName')" />
      <el-table-column prop="code" :label="t('role.roleCode')" />
      <el-table-column :label="t('common.operation')" width="100">
        <template #default="{ row }">
          <el-button link type="danger" @click="handleUnbindRole(row)">
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { businessUnitApi, type BusinessUnit } from '@/api/businessUnit'
import { roleApi, type Role } from '@/api/role'

const props = defineProps<{ businessUnit: BusinessUnit | null }>()
const visible = defineModel<boolean>({ default: false })
const { t } = useI18n()

const loading = ref(false)
const boundRoles = ref<any[]>([])
const allRoles = ref<Role[]>([])
const selectedRoleId = ref('')

const availableRoles = computed(() => {
  const boundIds = new Set(boundRoles.value.map((r: any) => r.id))
  // 只显示 BU_BOUNDED 类型的角色作为准入角色
  return allRoles.value.filter(r => r.type === 'BU_BOUNDED' && !boundIds.has(r.id))
})

const fetchRoles = async () => {
  if (!props.businessUnit) return
  loading.value = true
  try {
    const [roles, bound] = await Promise.all([
      roleApi.list(),
      businessUnitApi.getBoundRoles(props.businessUnit.id)
    ])
    allRoles.value = roles
    boundRoles.value = bound
  } catch (e) {
    console.error('Failed to fetch roles:', e)
    ElMessage.error(t('common.failed'))
  } finally {
    loading.value = false
  }
}

const handleBindRole = async () => {
  if (!props.businessUnit || !selectedRoleId.value) return
  try {
    await businessUnitApi.bindRole(props.businessUnit.id, selectedRoleId.value)
    ElMessage.success(t('common.success'))
    selectedRoleId.value = ''
    fetchRoles()
  } catch (e) {
    console.error('Failed to bind role:', e)
    ElMessage.error(t('common.failed'))
  }
}

const handleUnbindRole = async (role: any) => {
  if (!props.businessUnit) return
  await ElMessageBox.confirm(t('common.confirm'), t('common.confirm'), { type: 'warning' })
  try {
    await businessUnitApi.unbindRole(props.businessUnit.id, role.id)
    ElMessage.success(t('common.success'))
    fetchRoles()
  } catch (e) {
    console.error('Failed to unbind role:', e)
    ElMessage.error(t('common.failed'))
  }
}

watch(() => props.businessUnit, () => { if (visible.value) fetchRoles() })
</script>

<style scoped>
.hint-text {
  color: #909399;
  font-size: 13px;
  margin-bottom: 16px;
}
.roles-header {
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>
