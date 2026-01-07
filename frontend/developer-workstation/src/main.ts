import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import FcDesigner from '@form-create/designer'
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
// 这些伪元素会在非设计器页面产生小方框
// 注意：排除 BPMN 流程图相关元素
const overrideStyle = document.createElement('style')
overrideStyle.textContent = `
  /* 只在图标库和功能单元列表页面禁用伪元素 */
  .icon-grid *::before,
  .icon-grid *::after,
  .function-unit-grid *::before,
  .function-unit-grid *::after {
    display: none !important;
    content: none !important;
  }
`
document.head.appendChild(overrideStyle)

app.mount('#app')
