<template>
  <el-config-provider :locale="locale">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import zhTw from 'element-plus/dist/locale/zh-tw.mjs'
import en from 'element-plus/dist/locale/en.mjs'

const { locale: currentLocale } = useI18n()

const localeMap: Record<string, any> = {
  'zh-CN': zhCn,
  'zh-TW': zhTw,
  'en': en
}

const locale = computed(() => localeMap[currentLocale.value] || en)
</script>
