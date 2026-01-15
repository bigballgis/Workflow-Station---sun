<template>
  <el-dialog
    v-model="visible"
    :title="t('virtualGroup.boundRole') + ' - ' + (group?.name || '')"
    width="600px"
    @open="fetchRoles"
  >
    <!-- System group warning -->
    <el-alert
      v-if="isSystemGroup"
      :title="t('virtualGroup.systemGroupRoleWarning')"
      type="warning"
      :closable="false"
      style="margin-bottom: 16px"
    />

    <div class="roles-header">
      <el-select 
        v-model="selectedRoleId" 
        :placeholder="t('virtualGroup.selectRolePlaceholder')" 
        filterable 
        style="width: 300px"
        :disabled="isSystemGroup"
      >
        <el-option
          v-for="role in availableRoles"
          :key="role.id"
          :label="`${role.name} (${getRoleTypeLabel(role.type)})`"
          :value="role.id"
        />
      </el-select>
      <el-button type="primary" @click="handleBindRole" :disabled="!selectedRoleId || isSystemGroup">
        {{ boundRole ? t('virtualGroup.replaceRole') : t('virtualGroup.bindRole') }}
      </el-button>
    </div>

    <el-alert
      v-if="boundRole"
      :title="t('virtualGroup.currentBoundRole')"
      type="info"
      :closable="false"
      style="margin-top: 16px"
    >
      <template #default>
        <div class="bound-role-info">
          <span class="role-name">{{ boundRole.roleName }}</span>
          <el-tag size="small" :type="boundRole.roleType === 'BU_BOUNDED' ? 'warning' : 'success'">
            {{ getRoleTypeLabel(boundRole.roleType) }}
          </el-tag>
          <el-button 
            v-if="!isSystemGroup"
            link 
            type="danger" 
            @click="handleUnbindRole" 
            style="margin-left: 16px"
          >
            {{ t('virtualGroup.unbindRole') }}
          </el-button>
        </div>
      </template>
    </el-alert>

    <el-empty v-else :description="t('virtualGroup.noRoleBound')" style="margin-top: 16px" />

    <div class="role-type-hint" style="margin-top: 16px; color: #909399; font-size: 13px;">
      <p>{{ t('virtualGroup.roleTypeHint') }}</p>
      <ul style="margin: 8px 0 0 20px; padding: 0;">
        <li><strong>{{ t('role.buBounded') }}</strong>: {{ t('virtualGroup.buBoundedHint') }}</li>
        <li><strong>{{ t('role.buUnbounded') }}</strong>: {{ t('virtualGroup.buUnboundedHint') }}</li>
      </ul>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { virtualGroupApi, type VirtualGroup, type VirtualGroupRole } from '@/api/virtualGroup'
import { roleApi, type Role } from '@/api/role'

const props = defineProps<{ group: VirtualGroup | null }>()
const visible = defineModel<boolean>({ default: false })
const emit = defineEmits<{ success: [] }>()
const { t } = useI18n()

const loading = ref(false)
const boundRole = ref<VirtualGroupRole | null>(null)
const allRoles = ref<Role[]>([])
const selectedRoleId = ref('')

// Check if the group is a system group (cannot modify role bindings)
const isSystemGroup = computed(() => props.group?.type === 'SYSTEM')

const availableRoles = computed(() => {
  // Only show BU_BOUNDED and BU_UNBOUNDED roles (business roles)
  return allRoles.value.filter(r => {
    const roleType = r.type as string
    return (roleType === 'BU_BOUNDED' || roleType === 'BU_UNBOUNDED' || roleType === 'BUSINESS') &&
      r.id !== boundRole.value?.roleId
  })
})

const getRoleTypeLabel = (type?: string) => {
  const map: Record<string, string> = {
    BU_BOUNDED: t('role.buBounded'),
    BU_UNBOUNDED: t('role.buUnbounded'),
    BUSINESS: t('role.businessRole'),
    ADMIN: t('role.adminRole'),
    DEVELOPER: t('role.developerRole')
  }
  return map[type || ''] || type
}

const fetchRoles = async () => {
  if (!props.group) return
  loading.value = true
  try {
    const [roles, boundRoles] = await Promise.all([
      roleApi.list(),
      virtualGroupApi.getBoundRoles(props.group.id)
    ])
    allRoles.value = roles
    // Single role binding - take the first one if exists
    boundRole.value = boundRoles.length > 0 ? boundRoles[0] : null
  } catch (e) {
    console.error('Failed to fetch roles:', e)
    ElMessage.error(t('common.failed'))
  } finally {
    loading.value = false
  }
}

const handleBindRole = async () => {
  if (!props.group || !selectedRoleId.value) return
  
  // If there's already a bound role, confirm replacement
  if (boundRole.value) {
    try {
      await ElMessageBox.confirm(
        t('virtualGroup.replaceRoleConfirm'),
        t('common.confirm'),
        { type: 'warning' }
      )
    } catch {
      return
    }
  }
  
  try {
    await virtualGroupApi.bindRole(props.group.id, selectedRoleId.value)
    ElMessage.success(t('common.success'))
    selectedRoleId.value = ''
    fetchRoles()
    emit('success')
  } catch (e) {
    console.error('Failed to bind role:', e)
    ElMessage.error(t('common.failed'))
  }
}

const handleUnbindRole = async () => {
  if (!props.group || !boundRole.value) return
  await ElMessageBox.confirm(t('virtualGroup.unbindRoleConfirm'), t('common.confirm'), { type: 'warning' })
  try {
    await virtualGroupApi.unbindRole(props.group.id, boundRole.value.roleId)
    ElMessage.success(t('common.success'))
    fetchRoles()
    emit('success')
  } catch (e) {
    console.error('Failed to unbind role:', e)
    ElMessage.error(t('common.failed'))
  }
}

watch(() => props.group, () => { if (visible.value) fetchRoles() })
</script>

<style scoped>
.roles-header {
  display: flex;
  gap: 12px;
  align-items: center;
}
.bound-role-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.role-name {
  font-weight: 500;
}
</style>
