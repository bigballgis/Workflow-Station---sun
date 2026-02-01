import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import FcDesigner from '@form-create/designer'
// 导入 form-create 样式
import '@form-create/designer/src/style/index.css'
import '@form-create/designer/src/style/icon.css'
import '@form-create/element-ui/src/style/index.css'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import './styles/index.scss'

const app = createApp(App)

// 注册Element Plus图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.use(i18n)
app.use(FcDesigner)
app.use(FcDesigner.formCreate)

// 覆盖 form-create 库注入的全局伪元素样式
// form-create 使用 fc-icon 字体和 .icon-xxx:before 伪元素
const overrideStyle = document.createElement('style')
overrideStyle.id = 'fc-font-override'
overrideStyle.textContent = `
  /* 强制页面元素使用系统字体，但排除 form-create 设计器 */
  html, body, #app, 
  .page-container, .page-container *:not([class*="_fd-"]):not([class*="fc-"]):not(.fc-icon),
  .card, .card *:not([class*="_fd-"]):not([class*="fc-"]):not(.fc-icon),
  .el-input, .el-input *,
  .el-input__inner, 
  .el-input__wrapper,
  .el-select, .el-select *,
  .el-select__placeholder,
  .el-form-item:not([class*="_fd-"]) *,
  .el-button, .el-tag, .el-menu-item,
  .filter-form, .filter-form *,
  .tag-filter-item, .tag-filter-item *,
  input, textarea, select, button {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif !important;
  }
  
  /* placeholder 强制使用系统字体 */
  ::placeholder,
  ::-webkit-input-placeholder,
  ::-moz-placeholder,
  :-ms-input-placeholder,
  .el-input__inner::placeholder,
  input::placeholder,
  textarea::placeholder {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif !important;
  }
  
  /* 图标库页面 - 禁用所有伪元素 */
  .icon-grid::before,
  .icon-grid::after,
  .icon-grid .icon-item::before,
  .icon-grid .icon-item::after,
  .icon-grid .icon-preview::before,
  .icon-grid .icon-preview::after,
  .icon-grid .icon-name::before,
  .icon-grid .icon-name::after,
  .icon-selector__grid::before,
  .icon-selector__grid::after,
  .icon-selector__grid .icon-item::before,
  .icon-selector__grid .icon-item::after {
    content: none !important;
    display: none !important;
  }
  
  /* 确保 BPMN 图标字体正常 */
  [class*="bpmn-icon"] {
    font-family: 'bpmn' !important;
  }
  
  /* 确保 form-create 设计器图标字体正常 - 使用更高优先级选择器 */
  .fc-icon,
  i.fc-icon,
  [class*="fc-icon"],
  [class^="_fd-"] i,
  [class*=" _fd-"] i,
  ._fd-drag-tool i,
  ._fd-m i,
  ._fd-menu i,
  ._fc-designer i,
  .fc-designer i,
  ._fd-drag-btn i,
  ._fd-side-l i,
  ._fd-side-r i,
  ._fd-tool i,
  ._fd-m-tool i,
  ._fd-m-drag i {
    font-family: 'fc-icon' !important;
  }
  
  /* form-create 图标的伪元素也需要使用 fc-icon 字体 */
  .fc-icon::before,
  i.fc-icon::before,
  [class*="fc-icon"]::before,
  [class^="_fd-"] i::before,
  [class*=" _fd-"] i::before {
    font-family: 'fc-icon' !important;
  }
`
document.head.appendChild(overrideStyle)

// 添加全局错误处理，捕获未处理的 Promise 错误
// 这些错误通常来自浏览器扩展，不应该影响应用运行
// 使用 capture 模式确保最早捕获
window.addEventListener('unhandledrejection', (event) => {
  // 检查是否是来自浏览器扩展的错误（content.js）
  const error = event.reason
  
  // 更宽松的匹配条件，捕获所有可能的浏览器扩展错误
  if (error && typeof error === 'object') {
    // 检查错误特征，判断是否来自浏览器扩展
    const isExtensionError = 
      // 网络错误（httpStatus: 0）
      error.httpStatus === 0 ||
      // 网络错误文本
      error.httpStatusText === 'TypeError: Failed to fetch' ||
      // 浏览器扩展的典型错误格式：name: 'n', code: 0 或 code: 403
      (error.name === 'n' && (error.code === 0 || error.code === 403)) ||
      // 错误堆栈中包含 content.js
      (error.stack && typeof error.stack === 'string' && error.stack.includes('content.js')) ||
      // HTTP 状态码是 200 但 code 字段是 403（浏览器扩展的误判）
      (error.httpStatus === 200 && error.code === 403 && error.name === 'n') ||
      // 更宽松的条件：name 是 'n' 且 httpError 是 false（浏览器扩展的特征）
      (error.name === 'n' && error.httpError === false && error.httpStatus === 200) ||
      // 最宽松的条件：只要 name 是 'n' 且 httpStatus 是 200，就认为是扩展错误
      (error.name === 'n' && error.httpStatus === 200)
    
    if (isExtensionError) {
      // 静默忽略，不输出任何日志
      event.preventDefault() // 阻止错误在控制台显示
      event.stopPropagation() // 阻止事件传播
      event.stopImmediatePropagation() // 立即停止传播
      return false // 返回 false 表示已处理
    }
  }
  // 其他错误正常处理
  console.error('[GlobalErrorHandler] Unhandled promise rejection:', event.reason)
}, true) // 使用 capture 模式

// Vue 全局错误处理
app.config.errorHandler = (err, _instance, info) => {
  console.error('[VueErrorHandler]', err, info)
}

app.mount('#app')
