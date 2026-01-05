<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="`成员管理 - ${group?.name}`" width="700px">
    <div class="members-header">
      <el-button type="primary" size="small" @click="showAddDialog = true">添加成员</el-button>
    </div>
    
    <el-table :data="members" max-height="400">
      <el-table-column prop="realName" label="姓名" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="role" label="组内角色">
        <template #default="{ row }">
          <el-tag size="small">{{ row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="joinTime" label="加入时间" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="danger" @click="handleRemove(row)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <el-dialog v-model="showAddDialog" title="添加成员" width="400px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="选择用户">
          <el-select v-model="newMember.userId" filterable placeholder="搜索用户">
            <el-option v-for="user in availableUsers" :key="user.id" :label="user.realName" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="组内角色">
          <el-select v-model="newMember.role">
            <el-option label="组长" value="LEADER" />
            <el-option label="成员" value="MEMBER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAdd">确定</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps<{ modelValue: boolean; group: any }>()
const emit = defineEmits(['update:modelValue'])

const members = ref<any[]>([])
const showAddDialog = ref(false)
const availableUsers = ref([{ id: '1', realName: '张三' }, { id: '2', realName: '李四' }])
const newMember = reactive({ userId: '', role: 'MEMBER' })

watch(() => props.modelValue, (val) => {
  if (val && props.group) {
    members.value = [
      { id: '1', realName: '张三', username: 'zhangsan', role: '组长', joinTime: '2026-01-01' },
      { id: '2', realName: '李四', username: 'lisi', role: '成员', joinTime: '2026-01-02' }
    ]
  }
})

const handleRemove = async (member: any) => {
  await ElMessageBox.confirm('确定要移除该成员吗？', '提示', { type: 'warning' })
  members.value = members.value.filter(m => m.id !== member.id)
  ElMessage.success('移除成功')
}

const handleAdd = () => {
  if (!newMember.userId) return
  const user = availableUsers.value.find(u => u.id === newMember.userId)
  members.value.push({ id: newMember.userId, realName: user?.realName, username: 'user', role: newMember.role === 'LEADER' ? '组长' : '成员', joinTime: new Date().toISOString().split('T')[0] })
  showAddDialog.value = false
  newMember.userId = ''
  newMember.role = 'MEMBER'
  ElMessage.success('添加成功')
}
</script>

<style scoped>
.members-header { margin-bottom: 15px; }
</style>
