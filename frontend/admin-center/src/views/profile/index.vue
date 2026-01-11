<template>
  <div class="profile-container">
    <el-card class="profile-card">
      <template #header>
        <div class="card-header">
          <span>个人信息</span>
        </div>
      </template>
      
      <div class="profile-content" v-loading="loading">
        <div class="avatar-section">
          <el-avatar :size="100" :src="userInfo?.avatar || defaultAvatar">
            {{ (userInfo?.displayName || userInfo?.username || 'U').charAt(0).toUpperCase() }}
          </el-avatar>
          <h2>{{ userInfo?.displayName || userInfo?.username || '用户' }}</h2>
          <p class="user-role">{{ userInfo?.roles?.join(', ') || '普通用户' }}</p>
        </div>
        
        <el-divider />
        
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户名">
            {{ userInfo?.username || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">
            {{ userInfo?.email || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="用户ID">
            {{ userInfo?.userId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="部门ID">
            {{ userInfo?.departmentId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="语言">
            {{ userInfo?.language || 'zh-CN' }}
          </el-descriptions-item>
          <el-descriptions-item label="权限数">
            {{ userInfo?.permissions?.length || 0 }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>
    
    <el-card class="password-card">
      <template #header>
        <div class="card-header">
          <span>修改密码</span>
        </div>
      </template>
      
      <el-form 
        ref="passwordFormRef" 
        :model="passwordForm" 
        :rules="passwordRules" 
        label-width="100px"
      >
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input 
            v-model="passwordForm.oldPassword" 
            type="password" 
            show-password
            placeholder="请输入当前密码"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input 
            v-model="passwordForm.newPassword" 
            type="password" 
            show-password
            placeholder="请输入新密码"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input 
            v-model="passwordForm.confirmPassword" 
            type="password" 
            show-password
            placeholder="请再次输入新密码"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleChangePassword" :loading="changingPassword">
            修改密码
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import request from '@/api/request'

const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

interface UserInfo {
  userId?: string
  username?: string
  displayName?: string
  email?: string
  roles?: string[]
  permissions?: string[]
  departmentId?: string
  language?: string
  avatar?: string
}

const loading = ref(false)
const userInfo = ref<UserInfo | null>(null)
const passwordFormRef = ref<FormInstance>()
const changingPassword = ref(false)

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const passwordRules: FormRules = {
  oldPassword: [
    { required: true, message: '请输入当前密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const formatDate = (dateStr?: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

const loadUserInfo = async () => {
  loading.value = true
  try {
    // 优先从 localStorage 获取用户信息（登录时保存的）
    const storedUser = localStorage.getItem('user')
    if (storedUser) {
      try {
        userInfo.value = JSON.parse(storedUser)
        loading.value = false
        return
      } catch {
        // 继续尝试从 API 获取
      }
    }
    
    // 从 API 获取用户信息
    const response = await request.get('/api/v1/auth/me', { baseURL: '' })
    userInfo.value = response.data || response
  } catch (error) {
    console.error('Failed to load user info:', error)
    // 尝试从 localStorage 获取基本信息
    const storedUser = localStorage.getItem('userInfo')
    if (storedUser) {
      try {
        userInfo.value = JSON.parse(storedUser)
      } catch {
        userInfo.value = { username: localStorage.getItem('username') || '用户' }
      }
    }
  } finally {
    loading.value = false
  }
}

const handleChangePassword = async () => {
  if (!passwordFormRef.value) return
  
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return
    
    changingPassword.value = true
    try {
      await request.post('/api/v1/auth/change-password', {
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      }, { baseURL: '' })
      ElMessage.success('密码修改成功')
      passwordFormRef.value?.resetFields()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '密码修改失败')
    } finally {
      changingPassword.value = false
    }
  })
}

onMounted(() => {
  loadUserInfo()
})
</script>

<style scoped>
.profile-container {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.profile-card {
  margin-bottom: 20px;
}

.password-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.profile-content {
  min-height: 200px;
}

.avatar-section {
  text-align: center;
  padding: 20px 0;
}

.avatar-section h2 {
  margin: 15px 0 5px;
  font-size: 20px;
}

.user-role {
  color: #909399;
  font-size: 14px;
}
</style>
