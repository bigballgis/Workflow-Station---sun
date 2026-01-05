<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="`${t('role.members')} - ${role?.name}`" width="700px">
    <div class="members-header">
      <el-button type="primary" size="small" @click="showAddDialog = true">添加成员</el-button>
    </div>
    
    <el-table :data="members" v-loading="loading" max-height="400">
      <el-table-column prop="realName" label="姓名" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="departmentName" label="部门" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="danger" @click="handleRemove(row)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <el-dialog v-model="showAddDialog" title="添加成员" width="500px" append-to-body>
      <el-transfer v-model="selectedUsers" :data="availableUsers" :titles="['可选用户', '已选用户']" :props="{ key: 'id', label: 'realName' }" filterable />
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAdd">确定</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Role, roleApi } from '@/api/role'

const props = defineProps<{ modelValue: boolean; role: Role | null }>()
const emit = defineEmits(['update:modelValue'])

const { t } = useI18n()

const loading = ref(false)
const members = ref<any[]>([])
const showAddDialog = ref(false)
const availableUsers = ref<any[]>([])
const selectedUsers = ref<string[]>([])

watch(() => props.modelValue, async (val) => {
  if (val && props.role) {
    loading.value = true
    try {
      const result = await roleApi.getMembers(props.role.id, { page: 0, size: 100 })
      members.value = result.content || []
    } finally {
      loading.value = false
    }
  }
})

const handleRemove = async (user: any) => {
  await ElMessageBox.confirm('确定要移除该成员吗？', '提示', { type: 'warning' })
  await roleApi.removeMembers(props.role!.id, [user.id])
  members.value = members.value.filter(m => m.id !== user.id)
  ElMessage.success(t('common.success'))
}

const handleAdd = async () => {
  if (selectedUsers.value.length === 0) return
  await roleApi.addMembers(props.role!.id, selectedUsers.value)
  showAddDialog.value = false
  selectedUsers.value = []
  // Refresh members
  const result = await roleApi.getMembers(props.role!.id, { page: 0, size: 100 })
  members.value = result.content || []
  ElMessage.success(t('common.success'))
}
</script>

<style scoped>
.members-header {
  margin-bottom: 15px;
}
</style>
