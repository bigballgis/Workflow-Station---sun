<template>
  <el-dialog 
    :model-value="modelValue" 
    @update:model-value="$emit('update:modelValue', $event)" 
    :title="isEdit ? '编辑用户' : '创建用户'" 
    width="560px"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" :disabled="isEdit" placeholder="请输入用户名" />
      </el-form-item>
      <el-form-item label="姓名" prop="fullName">
        <el-input v-model="form.fullName" placeholder="请输入姓名" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" placeholder="请输入邮箱" />
      </el-form-item>
      <el-form-item label="工号" prop="employeeId">
        <el-input v-model="form.employeeId" placeholder="请输入工号" />
      </el-form-item>
      <el-form-item label="部门" prop="departmentId">
        <el-tree-select 
          v-model="form.departmentId" 
          :data="departmentTree" 
          :props="{ label: 'name', children: 'children' }" 
          node-key="id"
          clearable 
          check-strictly 
          placeholder="请选择部门"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="职位" prop="position">
        <el-input v-model="form.position" placeholder="请输入职位" />
      </el-form-item>
      <el-form-item label="实体管理者" prop="entityManagerId">
        <el-select 
          v-model="form.entityManagerId" 
          filterable 
          remote 
          :remote-method="searchUsers"
          clearable 
          placeholder="搜索并选择实体管理者"
          style="width: 100%"
          :loading="userSearchLoading"
        >
          <el-option 
            v-for="user in userOptions" 
            :key="user.id" 
            :label="`${user.fullName} (${user.username})`" 
            :value="user.id" 
          />
        </el-select>
      </el-form-item>
      <el-form-item label="职能管理者" prop="functionManagerId">
        <el-select 
          v-model="form.functionManagerId" 
          filterable 
          remote 
          :remote-method="searchUsers"
          clearable 
          placeholder="搜索并选择职能管理者"
          style="width: 100%"
          :loading="userSearchLoading"
        >
          <el-option 
            v-for="user in userOptions" 
            :key="user.id" 
            :label="`${user.fullName} (${user.username})`" 
            :value="user.id" 
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="!isEdit" label="初始密码" prop="initialPassword">
        <el-input v-model="form.initialPassword" type="password" show-password placeholder="请输入初始密码" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useOrganizationStore } from '@/stores/organization'
import { userApi, type User } from '@/api/user'

const props = defineProps<{ modelValue: boolean; user: User | null }>()
const emit = defineEmits(['update:modelValue', 'success'])

const orgStore = useOrganizationStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const userSearchLoading = ref(false)
const userOptions = ref<{ id: string; fullName: string; username: string }[]>([])
const isEdit = computed(() => !!props.user)
const departmentTree = computed(() => orgStore.departmentTree)

const form = reactive({
  username: '',
  fullName: '',
  email: '',
  employeeId: '',
  departmentId: '',
  position: '',
  entityManagerId: '',
  functionManagerId: '',
  initialPassword: ''
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度为3-50个字符', trigger: 'blur' }
  ],
  fullName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  initialPassword: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 8, message: '密码长度至少8位', trigger: 'blur' }
  ]
}

watch(() => props.modelValue, (val) => {
  if (val) {
    orgStore.fetchTree()
    loadDefaultUsers() // 加载默认用户列表
    if (props.user) {
      Object.assign(form, {
        username: props.user.username,
        fullName: props.user.fullName,
        email: props.user.email,
        employeeId: props.user.employeeId || '',
        departmentId: props.user.departmentId || '',
        position: props.user.position || '',
        entityManagerId: (props.user as any).entityManagerId || '',
        functionManagerId: (props.user as any).functionManagerId || '',
        initialPassword: ''
      })
      // 加载已选管理者信息
      loadSelectedManagers()
    } else {
      Object.assign(form, {
        username: '', fullName: '', email: '',
        employeeId: '', departmentId: '', position: '',
        entityManagerId: '', functionManagerId: '',
        initialPassword: ''
      })
    }
  }
})

const loadSelectedManagers = async () => {
  const managerIds = [form.entityManagerId, form.functionManagerId].filter(Boolean)
  if (managerIds.length === 0) return
  
  try {
    const managers: { id: string; fullName: string; username: string }[] = []
    for (const id of managerIds) {
      const user = await userApi.getById(id)
      if (user) {
        managers.push({
          id: user.id,
          fullName: user.fullName,
          username: user.username
        })
      }
    }
    // 合并已选管理者到选项列表（去重）
    const existingIds = new Set(userOptions.value.map(u => u.id))
    for (const mgr of managers) {
      if (!existingIds.has(mgr.id)) {
        userOptions.value.push(mgr)
      }
    }
  } catch (error) {
    console.error('Failed to load managers:', error)
  }
}

// 加载默认用户列表（前3个）
const loadDefaultUsers = async () => {
  userSearchLoading.value = true
  try {
    const res = await userApi.list({ page: 0, size: 3 })
    userOptions.value = (res.content || []).map((u) => ({
      id: u.id,
      fullName: u.fullName,
      username: u.username
    }))
  } catch (error) {
    console.error('Failed to load default users:', error)
    userOptions.value = []
  } finally {
    userSearchLoading.value = false
  }
}

const searchUsers = async (query: string) => {
  // 如果没有输入，显示默认的前3个用户
  if (!query) {
    await loadDefaultUsers()
    return
  }
  
  userSearchLoading.value = true
  try {
    const res = await userApi.list({ keyword: query, page: 0, size: 20 })
    userOptions.value = (res.content || []).map((u) => ({
      id: u.id,
      fullName: u.fullName,
      username: u.username
    }))
  } catch (error) {
    console.error('Failed to search users:', error)
    userOptions.value = []
  } finally {
    userSearchLoading.value = false
  }
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  
  loading.value = true
  try {
    if (isEdit.value) {
      await userApi.update(props.user!.id, {
        fullName: form.fullName,
        email: form.email,
        employeeId: form.employeeId || undefined,
        departmentId: form.departmentId || undefined,
        position: form.position || undefined,
        entityManagerId: form.entityManagerId || undefined,
        functionManagerId: form.functionManagerId || undefined
      })
    } else {
      await userApi.create({
        username: form.username,
        fullName: form.fullName,
        email: form.email,
        employeeId: form.employeeId || undefined,
        departmentId: form.departmentId || undefined,
        position: form.position || undefined,
        entityManagerId: form.entityManagerId || undefined,
        functionManagerId: form.functionManagerId || undefined,
        initialPassword: form.initialPassword
      })
    }
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    emit('update:modelValue', false)
    emit('success')
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    loading.value = false
  }
}
</script>
