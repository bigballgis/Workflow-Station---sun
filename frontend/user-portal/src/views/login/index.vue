<template>
  <div class="login-container">
    <!-- Language Switcher -->
    <div class="language-switcher">
      <el-dropdown @command="handleLanguage">
        <span class="lang-trigger">
          <el-icon><Location /></el-icon>
          <span>{{ currentLang }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="zh-CN">简体中文</el-dropdown-item>
            <el-dropdown-item command="zh-TW">繁體中文</el-dropdown-item>
            <el-dropdown-item command="en">English</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <div class="login-card">
      <div class="login-header">
        <h1>{{ t('login.title') }}</h1>
        <p>{{ t('login.subtitle') }}</p>
      </div>
      
      <!-- 测试用户快速选择 (仅开发环境) -->
      <div v-if="isDev" class="test-user-section">
        <el-divider content-position="center">
          <span class="test-user-label">{{ t('login.testUserQuickLogin') }}</span>
        </el-divider>
        <el-select 
          v-model="selectedTestUser" 
          :placeholder="t('login.selectTestUser')" 
          @change="onTestUserSelect"
          size="large"
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

      <el-form ref="formRef" :model="form" :rules="rules" class="login-form">
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            :placeholder="t('login.username')"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="t('login.password')"
            :prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            {{ t('login.login') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, FormInstance } from 'element-plus'
import { User, Lock, Location, ArrowDown } from '@element-plus/icons-vue'
import { login as authLogin, saveTokens, saveUser } from '@/api/auth'
import i18n from '@/i18n'

const { t } = useI18n()

const langMap: Record<string, string> = { 'zh-CN': '简体中文', 'zh-TW': '繁體中文', 'en': 'English' }
const currentLang = computed(() => langMap[i18n.global.locale.value] || '简体中文')

const handleLanguage = (lang: string) => {
  i18n.global.locale.value = lang as 'zh-CN' | 'zh-TW' | 'en'
  localStorage.setItem('language', lang)
}
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

// 开发环境检测
const isDev = import.meta.env.DEV

// 测试用户数据 (仅开发环境使用) - 采购流程测试用户
const testUsers = [
  // Purchase Workflow Test Users
  { username: 'purchase.requester', password: 'admin123', name: 'Tom Wilson', role: 'Initiator (IT-DEV)', tagType: 'primary' as const },
  { username: 'dept.reviewer', password: 'admin123', name: 'Alice Johnson', role: 'Dept Reviewer (IT-DEV)', tagType: 'success' as const },
  { username: 'parent.reviewer', password: 'admin123', name: 'Bob Smith', role: 'Senior Approver (IT)', tagType: 'warning' as const },
  { username: 'finance.reviewer', password: 'admin123', name: 'Carol Davis', role: 'Finance Reviewer', tagType: 'danger' as const },
  { username: 'countersign.approver1', password: 'admin123', name: 'Daniel Brown', role: 'Countersign Approver', tagType: 'info' as const },
  { username: 'countersign.approver2', password: 'admin123', name: 'Eva Martinez', role: 'Countersign Approver', tagType: 'info' as const },
  // Existing Manager Users
  { username: 'core.lead', password: 'admin123', name: 'Kevin Huang', role: 'Entity Manager', tagType: 'warning' as const },
  { username: 'tech.director', password: 'admin123', name: 'Robert Sun', role: 'Function Manager', tagType: 'danger' as const },
]

const selectedTestUser = ref('')

const form = reactive({
  username: '',
  password: ''
})

const rules = computed(() => ({
  username: [{ required: true, message: t('login.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.passwordRequired'), trigger: 'blur' }]
}))

// 选择测试用户时自动填充
const onTestUserSelect = (username: string) => {
  const user = testUsers.find(u => u.username === username)
  if (user) {
    form.username = user.username
    form.password = user.password
    ElMessage.info(`${t('login.selected')}: ${user.name}`)
  }
}

const handleLogin = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        // Call real login API
        const response = await authLogin({
          username: form.username,
          password: form.password
        })
        
        // Save tokens and user info
        saveTokens(response.accessToken, response.refreshToken)
        saveUser(response.user)
        localStorage.setItem('userId', response.user.userId)
        
        ElMessage.success(t('login.loginSuccess'))
        router.push('/dashboard')
      } catch (error: any) {
        const message = error.response?.data?.message || error.message || t('login.loginFailed')
        ElMessage.error(message)
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, var(--hsbc-red) 0%, #8B0000 100%);
  position: relative;
}

.language-switcher {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 10;

  .lang-trigger {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 16px;
    background: rgba(255, 255, 255, 0.15);
    border-radius: 20px;
    color: white;
    cursor: pointer;
    font-size: 14px;
    transition: background-color 0.3s;

    &:hover {
      background: rgba(255, 255, 255, 0.25);
    }
  }
}

.login-card {
  width: 400px;
  padding: 40px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
  
  h1 {
    font-size: 28px;
    color: var(--hsbc-red);
    margin: 0 0 8px 0;
  }
  
  p {
    font-size: 14px;
    color: var(--text-secondary);
    margin: 0;
  }
}

.login-form {
  .login-btn {
    width: 100%;
  }
}

.test-user-section {
  margin-bottom: 10px;
}

.test-user-label {
  font-size: 12px;
  color: #909399;
}

.test-user-select {
  width: 100%;
  margin-bottom: 16px;
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
</style>
