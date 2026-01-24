<template>
  <el-dialog 
    :model-value="modelValue" 
    @update:model-value="$emit('update:modelValue', $event)" 
    :title="t('role.members') + ' - ' + (role?.name || '')" 
    width="900px" 
    destroy-on-close
  >
    <!-- 角色信息卡片 -->
    <el-card shadow="never" class="info-card">
      <template #header>
        <div class="card-header">
          <span>{{ t('role.roleInfo') }}</span>
        </div>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item :label="t('role.roleName')">{{ role?.name }}</el-descriptions-item>
        <el-descriptions-item :label="t('role.roleCode')">{{ role?.code }}</el-descriptions-item>
        <el-descriptions-item :label="t('role.roleType')">
          <el-tag :type="typeTagType(role?.type)" size="small">{{ typeText(role?.type) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('virtualGroup.boundRole')" :span="3">
          <template v-if="boundVirtualGroup">
            <el-tag type="primary" size="small">{{ boundVirtualGroup.name }}</el-tag>
            <span class="text-muted" style="margin-left: 8px">({{ boundVirtualGroup.code }})</span>
          </template>
          <span v-else class="text-muted">{{ t('role.noVirtualGroupBound') }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
    
    <!-- 成员列表 -->
    <div class="section-title">
      <span>{{ t('role.memberList') }}</span>
      <span class="member-count">{{ members.length }} {{ t('role.people') }}</span>
    </div>
    
    <el-table :data="members" v-loading="loading" max-height="350" stripe size="small">
      <el-table-column type="index" width="50" align="center" />
      <el-table-column prop="employeeId" :label="t('user.employeeId')" width="100" />
      <el-table-column prop="username" :label="t('user.username')" width="120" />
      <el-table-column prop="fullName" :label="t('user.fullName')" width="100" />
      <el-table-column prop="email" :label="t('user.email')" min-width="180" show-overflow-tooltip />
      <el-table-column prop="businessUnitName" :label="t('user.businessUnit')" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.businessUnitName || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="joinedAt" :label="t('virtualGroup.joinedAt')" width="160">
        <template #default="{ row }">
          {{ formatDate(row.joinedAt) }}
        </template>
      </el-table-column>
    </el-table>
    
    <el-empty v-if="members.length === 0 && !loading && boundVirtualGroup" :description="t('common.noData')" />
    
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">{{ t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { type Role } from '@/api/role'
import { virtualGroupApi, type VirtualGroup, type VirtualGroupMember } from '@/api/virtualGroup'

const { t } = useI18n()

const props = defineProps<{ modelValue: boolean; role: Role | null }>()
defineEmits(['update:modelValue'])

const loading = ref(false)
const members = ref<VirtualGroupMember[]>([])
const boundVirtualGroup = ref<VirtualGroup | null>(null)

const typeText = (type?: string) => ({ 
  BU_BOUNDED: t('role.buBounded'), 
  BU_UNBOUNDED: t('role.buUnbounded'), 
  ADMIN: t('role.adminRole'), 
  DEVELOPER: t('role.developerRole') 
}[type || ''] || type || '-')

const typeTagType = (type?: string) => ({ 
  BU_BOUNDED: 'warning', 
  BU_UNBOUNDED: 'success', 
  ADMIN: 'danger', 
  DEVELOPER: 'primary' 
}[type || ''] || 'info') as any

const formatDate = (date?: string) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN', { 
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

// 获取角色绑定的虚拟组
const getBoundVirtualGroup = async (roleId: string): Promise<VirtualGroup | null> => {
  try {
    const groups = await virtualGroupApi.list()
    return groups.find(g => g.boundRoleId === roleId) || null
  } catch (error) {
    console.error('Failed to get bound virtual group:', error)
    return null
  }
}

watch(() => props.modelValue, async (val) => {
  if (val && props.role) {
    loading.value = true
    members.value = []
    boundVirtualGroup.value = null
    
    try {
      boundVirtualGroup.value = await getBoundVirtualGroup(props.role.id)
      if (boundVirtualGroup.value) {
        members.value = await virtualGroupApi.getMembers(boundVirtualGroup.value.id)
      }
    } catch (error) {
      console.error('Failed to load role members:', error)
    } finally {
      loading.value = false
    }
  }
})
</script>

<style scoped>
.info-card {
  margin-bottom: 16px;
}

.card-header {
  font-weight: 500;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 500;
}

.member-count {
  font-size: 13px;
  color: #909399;
  font-weight: normal;
}

.text-muted {
  color: #909399;
}
</style>
