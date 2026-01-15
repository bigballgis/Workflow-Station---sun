<template>
  <div class="member-management-page">
    <div class="page-header">
      <h1>{{ t('memberManagement.title') }}</h1>
    </div>

    <el-tabs v-model="activeTab">
      <!-- 虚拟组成员 -->
      <el-tab-pane :label="t('memberManagement.virtualGroupMembers')" name="virtualGroup">
        <div class="portal-card">
          <div class="filter-row">
            <el-select v-model="selectedVirtualGroup" :placeholder="t('memberManagement.selectVirtualGroup')" 
                       style="width: 300px" filterable @change="loadVirtualGroupMembers">
              <el-option v-for="group in managedVirtualGroups" :key="group.id" :label="group.name" :value="group.id" />
            </el-select>
          </div>
          
          <el-empty v-if="!selectedVirtualGroup" :description="t('memberManagement.selectVirtualGroup')" />
          <el-empty v-else-if="!loadingVG && virtualGroupMembers.length === 0" :description="t('memberManagement.noMembers')" />
          
          <el-table v-else :data="virtualGroupMembers" stripe v-loading="loadingVG">
            <el-table-column prop="fullName" :label="t('memberManagement.memberName')" width="150">
              <template #default="{ row }">{{ row.fullName || row.username }}</template>
            </el-table-column>
            <el-table-column prop="username" :label="t('memberManagement.username')" width="150" />
            <el-table-column prop="joinedAt" :label="t('memberManagement.joinTime')" width="160">
              <template #default="{ row }">{{ formatDate(row.joinedAt) }}</template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="100">
              <template #default="{ row }">
                <el-button type="danger" link size="small" @click="removeVGMember(row)">{{ t('memberManagement.remove') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 业务单元成员 -->
      <el-tab-pane :label="t('memberManagement.businessUnitMembers')" name="businessUnit">
        <div class="portal-card">
          <div class="filter-row">
            <el-select v-model="selectedBusinessUnit" :placeholder="t('memberManagement.selectBusinessUnit')" 
                       style="width: 300px" filterable @change="loadBusinessUnitMembers">
              <el-option v-for="bu in managedBusinessUnits" :key="bu.id" :label="bu.name" :value="bu.id" />
            </el-select>
          </div>
          
          <el-empty v-if="!selectedBusinessUnit" :description="t('memberManagement.selectBusinessUnit')" />
          <el-empty v-else-if="!loadingBU && businessUnitMembers.length === 0" :description="t('memberManagement.noMembers')" />
          
          <el-table v-else :data="businessUnitMembers" stripe v-loading="loadingBU">
            <el-table-column prop="fullName" :label="t('memberManagement.memberName')" width="150">
              <template #default="{ row }">{{ row.fullName || row.username }}</template>
            </el-table-column>
            <el-table-column prop="username" :label="t('memberManagement.username')" width="150" />
            <el-table-column prop="roles" :label="t('memberManagement.roles')" min-width="200">
              <template #default="{ row }">
                <el-tag v-for="role in row.roles" :key="role.id" size="small" style="margin-right: 4px">
                  {{ role.name }}
                  <el-icon class="remove-role-icon" @click.stop="removeBURole(row, role)"><Close /></el-icon>
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="joinedAt" :label="t('memberManagement.joinTime')" width="160">
              <template #default="{ row }">{{ formatDate(row.joinedAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close } from '@element-plus/icons-vue'
import { permissionApi, type MemberInfo, type VirtualGroupInfo, type BusinessUnit, type RoleInfo } from '@/api/permission'

const { t } = useI18n()

const activeTab = ref('virtualGroup')
const loadingVG = ref(false)
const loadingBU = ref(false)

const managedVirtualGroups = ref<VirtualGroupInfo[]>([])
const managedBusinessUnits = ref<BusinessUnit[]>([])
const selectedVirtualGroup = ref('')
const selectedBusinessUnit = ref('')
const virtualGroupMembers = ref<MemberInfo[]>([])
const businessUnitMembers = ref<MemberInfo[]>([])

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

const loadManagedGroups = async () => {
  try {
    // 获取当前用户作为审批人管理的虚拟组
    const res = await permissionApi.getAvailableVirtualGroups()
    managedVirtualGroups.value = res.data?.data || res.data || res || []
  } catch (e) {
    console.error('Failed to load managed virtual groups:', e)
  }
}

const loadManagedBusinessUnits = async () => {
  try {
    const res = await permissionApi.getBusinessUnits()
    managedBusinessUnits.value = res.data?.data || res.data || res || []
  } catch (e) {
    console.error('Failed to load managed business units:', e)
  }
}

const loadVirtualGroupMembers = async () => {
  if (!selectedVirtualGroup.value) return
  loadingVG.value = true
  try {
    const res = await permissionApi.getVirtualGroupMembers(selectedVirtualGroup.value)
    virtualGroupMembers.value = res.data?.data || res.data || res || []
  } catch (e) {
    console.error('Failed to load virtual group members:', e)
    virtualGroupMembers.value = []
  } finally {
    loadingVG.value = false
  }
}

const loadBusinessUnitMembers = async () => {
  if (!selectedBusinessUnit.value) return
  loadingBU.value = true
  try {
    const res = await permissionApi.getBusinessUnitMembers(selectedBusinessUnit.value)
    businessUnitMembers.value = res.data?.data || res.data || res || []
  } catch (e) {
    console.error('Failed to load business unit members:', e)
    businessUnitMembers.value = []
  } finally {
    loadingBU.value = false
  }
}

const removeVGMember = async (member: MemberInfo) => {
  try {
    await ElMessageBox.confirm(t('memberManagement.removeConfirm'), t('common.confirm'))
    await permissionApi.removeVirtualGroupMember(selectedVirtualGroup.value, member.userId)
    ElMessage.success(t('memberManagement.removeSuccess'))
    loadVirtualGroupMembers()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || t('memberManagement.removeFailed'))
    }
  }
}

const removeBURole = async (member: MemberInfo, role: RoleInfo) => {
  try {
    await ElMessageBox.confirm(t('memberManagement.removeConfirm'), t('common.confirm'))
    await permissionApi.removeBusinessUnitRole(selectedBusinessUnit.value, member.userId, role.id)
    ElMessage.success(t('memberManagement.removeSuccess'))
    loadBusinessUnitMembers()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || t('memberManagement.removeFailed'))
    }
  }
}

onMounted(() => {
  loadManagedGroups()
  loadManagedBusinessUnits()
})
</script>

<style lang="scss" scoped>
.member-management-page {
  .page-header {
    margin-bottom: 20px;
    h1 { font-size: 24px; font-weight: 500; margin: 0; }
  }
  .filter-row {
    margin-bottom: 20px;
  }
  .remove-role-icon {
    margin-left: 4px;
    cursor: pointer;
    &:hover { color: var(--el-color-danger); }
  }
}
</style>
