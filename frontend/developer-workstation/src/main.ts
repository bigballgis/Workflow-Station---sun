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

app.mount('#app')
