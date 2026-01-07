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
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="form.phone" placeholder="请输入手机号" />
      </el-form-item>
      <el-form-item label="工号" prop="employeeId">
        <el-input v-model="form.employeeId" placeholder="请输入工号" />
      </el-form-item>
      <el-form-item label="部门" prop="departmentId">
        <el-tree-select 
          v-model="form.departmentId" 
          :data="departmentTree" 
          :props="{ label: 'name', value: 'id' }" 
          clearable 
          check-strictly 
          placeholder="请选择部门"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="职位" prop="position">
        <el-input v-model="form.position" placeholder="请输入职位" />
      </el-form-item>
      <el-form-item v-if="!isEdit" label="初始密码" prop="initialPassword">
        <el-input v-model="form.initialPassword" type="password" show-password placeholder="请输入初始密码" />
      </el-form-item>
      <el-form-item label="角色" prop="roleIds">
        <el-select v-model="form.roleIds" multiple placeholder="请选择角色" style="width: 100%">
          <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
        </el-select>
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
const isEdit = computed(() => !!props.user)
const departmentTree = computed(() => orgStore.departmentTree)
const roles = ref<{ id: string; name: string }[]>([])

const form = reactive({
  username: '',
  fullName: '',
  email: '',
  phone: '',
  employeeId: '',
  departmentId: '',
  position: '',
  initialPassword: '',
  roleIds: [] as string[]
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
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  initialPassword: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 8, message: '密码长度至少8位', trigger: 'blur' }
  ]
}

watch(() => props.modelValue, (val) => {
  if (val) {
    orgStore.fetchTree()
    loadRoles()
    if (props.user) {
      Object.assign(form, {
        username: props.user.username,
        fullName: props.user.fullName,
        email: props.user.email,
        phone: props.user.phone || '',
        employeeId: props.user.employeeId || '',
        departmentId: props.user.departmentId || '',
        position: props.user.position || '',
        initialPassword: '',
        roleIds: []
      })
    } else {
      Object.assign(form, {
        username: '', fullName: '', email: '', phone: '',
        employeeId: '', departmentId: '', position: '',
        initialPassword: '', roleIds: []
      })
    }
  }
})

const loadRoles = async () => {
  // TODO: 从角色API加载角色列表
  roles.value = [
    { id: '1', name: '系统管理员' },
    { id: '2', name: '普通用户' },
    { id: '3', name: '审计员' }
  ]
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
        phone: form.phone || undefined,
        employeeId: form.employeeId || undefined,
        departmentId: form.departmentId || undefined,
        position: form.position || undefined,
        roleIds: form.roleIds.length > 0 ? form.roleIds : undefined
      })
    } else {
      await userApi.create({
        username: form.username,
        fullName: form.fullName,
        email: form.email,
        phone: form.phone || undefined,
        employeeId: form.employeeId || undefined,
        departmentId: form.departmentId || undefined,
        position: form.position || undefined,
        initialPassword: form.initialPassword,
        roleIds: form.roleIds.length > 0 ? form.roleIds : undefined
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
