import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import FcDesigner from '@form-create/designer'
// Import form-create styles
import '@form-create/designer/src/style/index.css'
import '@form-create/designer/src/style/icon.css'
import '@form-create/element-ui/src/style/index.css'
// Import form-create English locale
import enLocale from '@form-create/designer/locale/en.js'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import './styles/index.scss'

// Force set HTML lang attribute to English
document.documentElement.lang = 'en'

const app = createApp(App)

// Register Element Plus icons
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.use(i18n)
// Set form-create designer to English locale
FcDesigner.useLocale(enLocale)
app.use(FcDesigner)
app.use(FcDesigner.formCreate)

// Override global pseudo-element styles injected by form-create library
// form-create uses fc-icon font and .icon-xxx:before pseudo-elements
const overrideStyle = document.createElement('style')
overrideStyle.id = 'fc-font-override'
overrideStyle.textContent = `
  /* Force page elements to use system font, excluding form-create designer */
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
  
  /* Force placeholder to use system font */
  ::placeholder,
  ::-webkit-input-placeholder,
  ::-moz-placeholder,
  :-ms-input-placeholder,
  .el-input__inner::placeholder,
  input::placeholder,
  textarea::placeholder {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif !important;
  }
  
  /* Icon library page - disable all pseudo-elements */
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
  
  /* Ensure BPMN icon font works correctly */
  [class*="bpmn-icon"] {
    font-family: 'bpmn' !important;
  }
  
  /* Ensure form-create designer icon font works correctly - use higher priority selectors */
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
  
  /* Pseudo-elements of form-create icons also need to use fc-icon font */
  .fc-icon::before,
  i.fc-icon::before,
  [class*="fc-icon"]::before,
  [class^="_fd-"] i::before,
  [class*=" _fd-"] i::before {
    font-family: 'fc-icon' !important;
  }
`
document.head.appendChild(overrideStyle)

// Add global error handler to catch unhandled Promise rejections
// These errors usually come from browser extensions and should not affect app operation
// Use capture mode to catch errors as early as possible
window.addEventListener('unhandledrejection', (event) => {
  // Check if the error comes from a browser extension (content.js)
  const error = event.reason
  
  // Use broader matching conditions to catch all possible browser extension errors
  if (error && typeof error === 'object') {
    // Check error characteristics to determine if it comes from a browser extension
    const isExtensionError = 
      // Network error (httpStatus: 0)
      error.httpStatus === 0 ||
      // Network error text
      error.httpStatusText === 'TypeError: Failed to fetch' ||
      // Typical browser extension error format: name: 'n', code: 0 or code: 403
      (error.name === 'n' && (error.code === 0 || error.code === 403)) ||
      // Error stack contains content.js
      (error.stack && typeof error.stack === 'string' && error.stack.includes('content.js')) ||
      // HTTP status is 200 but code field is 403 (browser extension false positive)
      (error.httpStatus === 200 && error.code === 403 && error.name === 'n') ||
      // Broader condition: name is 'n' and httpError is false (browser extension characteristic)
      (error.name === 'n' && error.httpError === false && error.httpStatus === 200) ||
      // Broadest condition: as long as name is 'n' and httpStatus is 200, treat as extension error
      (error.name === 'n' && error.httpStatus === 200)
    
    if (isExtensionError) {
      // Silently ignore, no log output
      event.preventDefault() // Prevent error from showing in console
      event.stopPropagation() // Stop event propagation
      event.stopImmediatePropagation() // Immediately stop propagation
      return false // Return false to indicate handled
    }
  }
  // Other errors are handled normally
  console.error('[GlobalErrorHandler] Unhandled promise rejection:', event.reason)
}, true) // Use capture mode

// Vue global error handler
app.config.errorHandler = (err, instance, info) => {
  console.error('[VueErrorHandler]', err, info)
}

app.mount('#app')
