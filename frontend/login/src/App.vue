<template>
  <div class="login-container">
    <div class="bg-shape" />

    <div class="login-right">
      <div class="login-card">
        <div class="login-header">
          <div class="brand-logo">
            <img
              :src="logoUrl"
              alt="HerMes"
              class="brand-logo-img"
            >
          </div>
          <h2 class="login-title">
            {{ $t('login.title') }}
          </h2>
        </div>

        <form
          class="login-form"
          @submit.prevent="onSubmit"
        >
          <div class="form-field">
            <label class="field-label">{{ $t('login.username') }}</label>
            <input
              v-model="username"
              type="text"
              autocomplete="username"
              required
              :placeholder="$t('login.usernamePlaceholder')"
            >
          </div>
          <div class="form-field">
            <label class="field-label">{{ $t('login.password') }}</label>
            <input
              v-model="password"
              type="password"
              autocomplete="current-password"
              required
              :placeholder="$t('login.passwordPlaceholder')"
            >
          </div>
          <p
            v-if="missingParams"
            class="form-error"
          >
            {{ $t('login.error.missingParams') }}
          </p>
          <p
            v-else-if="errorCode"
            class="form-error"
          >
            {{ $t(errorTranslator(errorCode), errorDetails) }}
          </p>
          <div class="btn-item">
            <button
              type="submit"
              class="login-btn"
              :disabled="loading"
            >
              {{ loading ? $t('login.submitting') : $t('login.submit') }}
            </button>
          </div>
        </form>

        <template v-if="dspEnabled">
          <div class="login-divider">
            <span>{{ $t('login.or') }}</span>
          </div>
          <div class="btn-item">
            <button
              type="button"
              class="login-btn login-btn-dsp"
              :disabled="dspLoading || loading"
              @click="onDspLogin"
            >
              {{ dspLoading ? $t('login.dspSubmitting') : $t('login.dsp') }}
            </button>
          </div>
        </template>

        <div class="login-footer">
          <span>© 2026 HerMes</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSsoParams } from '@/composables/useSsoParams'
import { useLogin } from '@/composables/useLogin'
import { useDspLogin } from '@/composables/useDspLogin'
import { errorTranslator } from '@/utils/errorTranslator'

const { t } = useI18n()
onMounted(() => { document.title = t('login.htmlTitle') })

const logoUrl = `${import.meta.env.BASE_URL}hermes-logo.svg`

const { clientId, redirectUri, state, missingParams } = useSsoParams()
const { username, password, loading, errorCode, errorDetails, onSubmit } = useLogin(clientId, redirectUri, state)

// DSP 免密入口显隐：默认所有环境都显示；仅当运行时/构建期显式 VITE_DSP_ENABLED=false 时隐藏。
// （后端是否真正受理仍由 platform.sso.dsp.enabled 决定；按钮显示≠免密一定可用。）
const dspEnabledValue = window.__LOGIN_RUNTIME_CONFIG__?.VITE_DSP_ENABLED || import.meta.env.VITE_DSP_ENABLED
const dspEnabled = ref(dspEnabledValue !== 'false')
const { dspLoading, onDspLogin } = useDspLogin(clientId, redirectUri, state, errorCode, errorDetails)
</script>

<style scoped>
/* DSP 免密入口：分隔线 + 次要按钮样式（复用 .login-btn 基础样式，仅做视觉区分） */
.login-divider {
  display: flex;
  align-items: center;
  text-align: center;
  margin: 16px 0;
  color: #9ca3af;
  font-size: 12px;
}
.login-divider::before,
.login-divider::after {
  content: '';
  flex: 1;
  border-bottom: 1px solid #e5e7eb;
}
.login-divider span {
  padding: 0 12px;
}
.login-btn-dsp {
  background: #fff;
  color: #db0011;
  border: 1px solid #db0011;
}
.login-btn-dsp:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
