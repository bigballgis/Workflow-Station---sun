<template>
  <div class="exit-role-page">
    <div class="page-header">
      <h1>{{ t('exitRole.title') }}</h1>
    </div>

    <el-tabs v-model="activeTab">
      <!-- 虚拟组 -->
      <el-tab-pane :label="t('exitRole.virtualGroups')" name="virtualGroups">
        <div class="portal-card">
          <el-empty v-if="!loading && memberships.virtualGroups.length === 0" :description="t('exitRole.noMemberships')" />
          
          <el-table v-else :data="memberships.virtualGroups" stripe v-loading="loading">
            <el-table-column prop="groupName" :label="t('permission.virtualGroup')" min-width="200" />
            <el-table-column prop="boundRoles" :label="t('permission.boundRoles')" min-width="200">
              <template #default="{ row }">
                <template v-if="row.boundRoles?.length">
                  <div v-for="role in row.boundRoles" :key="role.id" class="role-item">
                    <el-tag size="small" style="margin-right: 4px">{{ role.name }}</el-tag>
                    <el-tag size="small" :type="role.type === 'BU_BOUNDED' ? 'warning' : 'success'">
                      {{ role.type === 'BU_BOUNDED' ? t('permission.buBounded') : t('permission.buUnbounded') }}
                    </el-tag>
                  </div>
                </template>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="joinedAt" :label="t('exitRole.joinTime')" width="160">
              <template #default="{ row }">{{ formatDate(row.joinedAt) }}</template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="100">
              <template #default="{ row }">
                <el-button type="danger" link size="small" @click="exitVirtualGroup(row)">{{ t('exitRole.exit') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 业务单元 -->
      <el-tab-pane :label="t('exitRole.businessUnits')" name="businessUnits">
        <div class="portal-card">
          <el-empty v-if="!loading && memberships.businessUnits.length === 0" :description="t('exitRole.noMemberships')" />
          
          <el-table v-else :data="memberships.businessUnits" stripe v-loading="loading">
            <el-table-column prop="businessUnitName" :label="t('exitRole.businessUnit')" min-width="200" />
            <el-table-column prop="joinedAt" :label="t('exitRole.joinTime')" width="160">
              <template #default="{ row }">{{ formatDate(row.joinedAt) }}</template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="100">
              <template #default="{ row }">
                <el-button type="danger" link size="small" @click="exitBusinessUnit(row)">{{ t('exitRole.exit') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { permissionApi, type UserVirtualGroupMembership } from '@/api/permission'

const { t } = useI18n()

const activeTab = ref('virtualGroups')
const loading = ref(false)

interface BusinessUnitMembership {
  businessUnitId: string
  businessUnitName: string
  joinedAt?: string
}

const memberships = reactive<{
  virtualGroups: UserVirtualGroupMembership[]
  businessUnits: BusinessUnitMembership[]
}>({
  virtualGroups: [],
  businessUnits: []
})

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

const loadMemberships = async () => {
  loading.value = true
  try {
    const res = await permissionApi.getMyMemberships()
    const data = res.data?.data || res.data || res
    memberships.virtualGroups = data.virtualGroups || []
    // Convert businessUnitRoles to businessUnits (group by business unit)
    const buMap = new Map<string, BusinessUnitMembership>()
    if (data.businessUnitRoles) {
      for (const role of data.businessUnitRoles) {
        if (!buMap.has(role.businessUnitId)) {
          buMap.set(role.businessUnitId, {
            businessUnitId: role.businessUnitId,
            businessUnitName: role.businessUnitName,
            joinedAt: role.assignedAt
          })
        }
      }
    }
    // Also check for businessUnits directly if available
    if (data.businessUnits) {
      for (const bu of data.businessUnits) {
        if (!buMap.has(bu.businessUnitId || bu.id)) {
          buMap.set(bu.businessUnitId || bu.id, {
            businessUnitId: bu.businessUnitId || bu.id,
            businessUnitName: bu.businessUnitName || bu.name,
            joinedAt: bu.joinedAt
          })
        }
      }
    }
    memberships.businessUnits = Array.from(buMap.values())
  } catch (e) {
    console.error('Failed to load memberships:', e)
  } finally {
    loading.value = false
  }
}

const exitVirtualGroup = async (group: UserVirtualGroupMembership) => {
  try {
    await ElMessageBox.confirm(t('exitRole.exitConfirm'), t('common.confirm'))
    await permissionApi.exitVirtualGroup(group.groupId)
    ElMessage.success(t('exitRole.exitSuccess'))
    loadMemberships()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || t('exitRole.exitFailed'))
    }
  }
}

const exitBusinessUnit = async (bu: BusinessUnitMembership) => {
  try {
    await ElMessageBox.confirm(t('exitRole.exitConfirm'), t('common.confirm'))
    await permissionApi.exitBusinessUnit(bu.businessUnitId)
    ElMessage.success(t('exitRole.exitSuccess'))
    loadMemberships()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || t('exitRole.exitFailed'))
    }
  }
}

onMounted(loadMemberships)
</script>

<style lang="scss" scoped>
.exit-role-page {
  .page-header {
    margin-bottom: 20px;
    h1 { font-size: 24px; font-weight: 500; margin: 0; }
  }
  .role-item {
    display: flex;
    align-items: center;
    margin-bottom: 4px;
    &:last-child {
      margin-bottom: 0;
    }
  }
}
</style>
