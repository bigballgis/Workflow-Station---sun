import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'

export default [
  // 全局忽略
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      '**/*.d.ts'
    ]
  },

  // JS 基础推荐规则
  js.configs.recommended,

  // TypeScript 推荐规则
  ...tseslint.configs.recommended,

  // Vue3 推荐规则（flat config）
  ...pluginVue.configs['flat/recommended'],

  // Vue 文件使用 TypeScript parser
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser
      }
    }
  },

  // 项目级覆盖
  {
    rules: {
      // 允许单名单词组件名（App.vue / Login.vue 等）
      'vue/multi-word-component-names': 'off',
      // catch 省略绑定后引用 error/e 会在运行时 ReferenceError
      'no-undef': 'error'
    },
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node
      }
    }
  }
]
