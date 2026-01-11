<template>
  <div class="login-container">
    <div class="login-bg-pattern"></div>
    <div class="login-content">
      <div class="login-card">
        <div class="login-header">
          <span class="login-icon">🛡️</span>
          <h2 class="login-title">管理员中心</h2>
          <p class="login-subtitle">系统管理与配置平台</p>
        </div>
        
        <!-- 测试用户快速选择 (仅开发环境) -->
        <div v-if="isDev" class="test-user-section">
          <el-divider content-position="center">
            <span class="test-user-label">🚀 快速登录</span>
          </el-divider>
          <el-select 
            v-model="selectedTestUser" 
            placeholder="选择测试用户" 
            @change="onTestUserSelect"
            class="test-user-select"
          >
            <el-option
              v-for="user in testUsers"
              :key="user.username"
              :label="`${user.name} (${user.role})`"
              :value="user.username"
            >
              <div class="test-user-option">
                <span class="user-name">{{ user.name }}</span>
                <el-tag size="small" :type="user.tagType">{{ user.role }}</el-tag>
              </div>
            </el-option>
          </el-select>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleLogin" class="login-form">
          <el-form-item prop="username">
            <el-input 
              v-model="form.username" 
              placeholder="请输入用户名" 
              prefix-icon="User"
              size="large"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input 
              v-model="form.password" 
              type="password" 
              placeholder="请输入密码" 
              prefix-icon="Lock" 
              show-password
              size="large"
            />
          </el-form-item>
          <el-form-item>
            <el-button 
              type="primary" 
              native-type="submit" 
              :loading="loading" 
              class="login-btn"
              size="large"
            >
              {{ loading ? '登录中...' : '登 录' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="login-footer">
          <span>© 2024 工作流平台 · 管理员中心</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, FormInstance } from 'element-plus'
import { login as authLogin, saveTokens, saveUser, type LoginRequest } from '@/api/auth'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const isDev = import.meta.env.DEV

const testUsers = [
  { username: 'admin', password: 'admin123', name: '系统管理员', role: '系统管理员', tagType: 'danger' as const },
]

const selectedTestUser = ref('')
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const onTestUserSelect = (username: string) => {
  const user = testUsers.find(u => u.username === username)
  if (user) {
    form.username = user.username
    form.password = user.password
    ElMessage.info(`已选择: ${user.name}`)
  }
}

const handleLogin = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  
  loading.value = true
  try {
    const response = await authLogin({
      username: form.username,
      password: form.password
    })
    
    saveTokens(response.accessToken, response.refreshToken)
    saveUser(response.user)
    localStorage.setItem('userId', response.user.userId)
    
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (error: any) {
    const message = error.response?.data?.message || error.message || '登录失败'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
$primary-color: #DB0011;
$primary-dark: #8B0000;

.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, $primary-color 0%, $primary-dark 100%);
  position: relative;
  overflow: hidden;
}

.login-bg-pattern {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: 
    radial-gradient(circle at 20% 80%, rgba(255,255,255,0.1) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(255,255,255,0.08) 0%, transparent 50%),
    radial-gradient(circle at 40% 40%, rgba(255,255,255,0.05) 0%, transparent 30%);
  pointer-events: none;
}

.login-content {
  position: relative;
  z-index: 1;
}

.login-card {
  width: 420px;
  padding: 40px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;

  .login-icon {
    font-size: 48px;
    display: block;
    margin-bottom: 16px;
  }

  .login-title {
    font-size: 24px;
    font-weight: 600;
    color: #303133;
    margin: 0 0 8px 0;
  }

  .login-subtitle {
    font-size: 14px;
    color: #909399;
    margin: 0;
  }
}

.test-user-section {
  margin-bottom: 20px;

  :deep(.el-divider__text) {
    background: white;
  }
}

.test-user-label {
  font-size: 12px;
  color: #909399;
}

.test-user-select {
  width: 100%;
}

.test-user-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.user-name {
  font-size: 14px;
}

.login-form {
  :deep(.el-input__wrapper) {
    border-radius: 8px;
  }

  :deep(.el-form-item) {
    margin-bottom: 20px;
  }
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
  background: linear-gradient(135deg, $primary-color 0%, $primary-dark 100%);
  border: none;
  
  &:hover {
    background: linear-gradient(135deg, lighten($primary-color, 5%) 0%, lighten($primary-dark, 5%) 100%);
  }
}

.login-footer {
  text-align: center;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
  
  span {
    font-size: 12px;
    color: #c0c4cc;
  }
}
</style>
