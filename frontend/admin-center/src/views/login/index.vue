<template>
  <div class="login-container">
    <!-- Left side arrow/chevron background decoration -->
    <div class="login-bg-left">
      <div class="chevron-shape"></div>
    </div>

    <!-- Right side login card -->
    <div class="login-right">
      <div class="login-card">
        <div class="login-header">
          <!-- H logo -->
          <div class="brand-logo">
            <span class="brand-h">H</span>
          </div>
          <h2 class="login-title">Admin Centre</h2>
        </div>

        <!-- Test user quick select (dev only) -->
        <div v-if="isDev" class="test-user-section">
          <el-divider content-position="center">
            <span class="test-user-label">🚀 {{ t('login.testUserHint') }}</span>
          </el-divider>
          <el-select
            v-model="selectedTestUser"
            :placeholder="t('login.selectTestUser')"
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
            <label class="field-label">Username</label>
            <el-input
              v-model="form.username"
              placeholder="Enter Your Username"
              prefix-icon="User"
              size="large"
            />
          </el-form-item>
          <el-form-item prop="password">
            <label class="field-label">Password</label>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="Enter Your Password"
              prefix-icon="Lock"
              show-password
              size="large"
            />
          </el-form-item>
          <el-form-item class="btn-item">
            <el-button
              type="primary"
              native-type="submit"
              :loading="loading"
              class="login-btn"
              size="large"
            >
              {{ loading ? t('common.loading') : 'Log in' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="workflow-link">
          <a href="#" class="workflow-text">Workflow Platform</a>
        </div>

        <div class="login-footer">
          <span>@2026 HerMes</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, FormInstance } from 'element-plus'
import { login as authLogin, saveTokens, saveUser } from '@/api/auth'

const { t } = useI18n()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const isDev = import.meta.env.DEV

const testUsers = [
  { username: 'admin', password: 'admin123', name: 'System Admin', role: 'Admin', tagType: 'danger' as const },
]

const selectedTestUser = ref('')
const form = reactive({ username: '', password: '' })
const rules = computed(() => ({
  username: [{ required: true, message: t('login.usernamePlaceholder'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.passwordPlaceholder'), trigger: 'blur' }]
}))

const onTestUserSelect = (username: string) => {
  const user = testUsers.find(u => u.username === username)
  if (user) {
    form.username = user.username
    form.password = user.password
    ElMessage.info(`Selected: ${user.name}`)
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

    ElMessage.success(t('common.success'))
    router.push('/dashboard')
  } catch (error: any) {
    const message = error.response?.data?.message || error.message || t('common.failed')
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
$primary: #C8102E;
$primary-dark: #9B0020;
$primary-deeper: #7A0018;

.login-container {
  height: 100vh;
  display: flex;
  background: $primary;
  overflow: hidden;
  position: relative;
}

/* Left decorative area with chevron */
.login-bg-left {
  flex: 1;
  position: relative;
  background: $primary;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chevron-shape {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;

  /* Large left-pointing arrow using clip-path on a darker overlay */
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.15);
    clip-path: polygon(0 0, 60% 0, 100% 50%, 60% 100%, 0 100%);
  }

  /* Inner darker chevron pointing left */
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.12);
    clip-path: polygon(0 0, 45% 0, 85% 50%, 45% 100%, 0 100%);
  }
}

/* Right side with card */
.login-right {
  width: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 48px;
  background: $primary;
}

.login-card {
  width: 100%;
  max-width: 360px;
  padding: 40px 36px 28px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.25);
}

/* Brand logo */
.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  background: $primary;
  border-radius: 8px;
  margin-bottom: 14px;
}

.brand-h {
  color: #fff;
  font-size: 28px;
  font-weight: 800;
  font-family: serif;
  line-height: 1;
}

.login-title {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a1a;
  margin: 0;
}

/* Form */
.login-form {
  .field-label {
    display: block;
    font-size: 13px;
    font-weight: 500;
    color: #444;
    margin-bottom: 6px;
  }

  :deep(.el-form-item) {
    margin-bottom: 18px;
    flex-direction: column;
    align-items: flex-start;
  }

  :deep(.el-form-item__content) {
    width: 100%;
  }

  :deep(.el-input__wrapper) {
    border-radius: 6px;
    border: 1px solid #dcdfe6;
    box-shadow: none;

    &:hover, &.is-focus {
      border-color: $primary;
      box-shadow: none;
    }
  }

  :deep(.el-input__inner) {
    font-size: 14px;
  }
}

.btn-item {
  margin-top: 8px;
  margin-bottom: 0 !important;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 6px;
  background: $primary;
  border-color: $primary;
  letter-spacing: 0.3px;

  &:hover, &:focus {
    background: $primary-dark;
    border-color: $primary-dark;
  }

  &:active {
    background: $primary-deeper;
    border-color: $primary-deeper;
  }
}

/* Workflow link */
.workflow-link {
  text-align: center;
  margin-top: 16px;
}

.workflow-text {
  font-size: 14px;
  font-weight: 600;
  color: $primary;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}

/* Footer */
.login-footer {
  text-align: center;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;

  span {
    font-size: 12px;
    color: #bbb;
  }
}

/* Test user section */
.test-user-section {
  margin-bottom: 16px;

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
</style>
