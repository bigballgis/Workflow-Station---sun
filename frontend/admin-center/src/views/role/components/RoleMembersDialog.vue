<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="`成员管理 - ${role?.name}`" width="700px" destroy-on-close>
    <div class="members-header">
      <el-button type="primary" size="small" @click="showAddDialog = true">添加成员</el-button>
    </div>
    
    <el-table :data="members" v-loading="loading" max-height="400">
      <el-table-column prop="userId" label="用户ID" width="200" />
      <el-table-column prop="roleCode" label="角色编码" />
      <el-table-column prop="assignedAt" label="分配时间">
        <template #default="{ row }">
          {{ row.assignedAt ? new Date(row.assignedAt).toLocaleString('zh-CN') : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="danger" @click="handleRemove(row)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <el-dialog v-model="showAddDialog" title="添加成员" width="500px" append-to-body>
      <el-form :model="addForm" label-width="80px">
        <el-form-item label="用户ID">
          <el-input v-model="addForm.userId" placeholder="请输入用户ID" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="addForm.reason" type="textarea" placeholder="请输入添加原因（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="addLoading" @click="handleAdd">确定</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { type Role, roleApi } from '@/api/role'

const props = defineProps<{ modelValue: boolean; role: Role | null }>()
const emit = defineEmits(['update:modelValue'])

const loading = ref(false)
const addLoading = ref(false)
const members = ref<any[]>([])
const showAddDialog = ref(false)
const addForm = reactive({ userId: '', reason: '' })

watch(() => props.modelValue, async (val) => {
  if (val && props.role) {
    await loadMembers()
  }
})

const loadMembers = async () => {
  if (!props.role) return
  loading.value = true
  try {
    members.value = await roleApi.getMembers(props.role.id)
  } catch (error: any) {
    ElMessage.error(error.message || '加载成员失败')
  } finally {
    loading.value = false
  }
}

const handleRemove = async (member: any) => {
  try {
    await ElMessageBox.confirm('确定要移除该成员吗？', '提示', { type: 'warning' })
    await roleApi.removeMember(props.role!.id, member.userId, '管理员移除')
    members.value = members.value.filter(m => m.userId !== member.userId)
    ElMessage.success('移除成功')
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '移除失败')
    }
  }
}

const handleAdd = async () => {
  if (!addForm.userId.trim()) {
    ElMessage.warning('请输入用户ID')
    return
  }
  addLoading.value = true
  try {
    await roleApi.addMember(props.role!.id, addForm.userId.trim(), addForm.reason || undefined)
    showAddDialog.value = false
    addForm.userId = ''
    addForm.reason = ''
    await loadMembers()
    ElMessage.success('添加成功')
  } catch (error: any) {
    ElMessage.error(error.message || '添加失败')
  } finally {
    addLoading.value = false
  }
}
</script>

<style scoped>
.members-header {
  margin-bottom: 15px;
}
</style>
