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
              :class="{ 'is-loading': loading }">
              {{ loading ? $t('login.submitting') : $t('login.submit') }}
            </button>
          </div>
        </form>

        <div class="login-footer">
          <span>© 2026 HerMes</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSsoParams } from '@/composables/useSsoParams'
import { useLogin } from '@/composables/useLogin'
import { errorTranslator } from '@/utils/errorTranslator'

const { t } = useI18n()
onMounted(() => { document.title = t('login.htmlTitle') })

const logoUrl = `${import.meta.env.BASE_URL}hermes-logo.svg`

const { clientId, redirectUri, state, missingParams } = useSsoParams()
const { username, password, loading, errorCode, errorDetails, onSubmit } = useLogin(clientId, redirectUri, state)
</script>
