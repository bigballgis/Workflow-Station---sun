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
import SubTablePlaceholderWidget from './components/designer/SubTablePlaceholderWidget.vue'
import SubTableBindingSelect from './components/designer/SubTableBindingSelect.vue'
import { FcEditor, FcTransfer, FcCascader, FcSlider } from './components/designer/fc-custom-fields'
import LookupComponent from './components/designer/LookupComponent.vue'
import LookupBindingSelect from './components/designer/LookupBindingSelect.vue'

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

// Register SubTableBindingSelect into both designerForm (props panel) and formCreate (canvas)
// FcDesigner.component() calls addComponent() which registers to both instances
FcDesigner.component('SubTableBindingSelect', SubTableBindingSelect)

// Register SubTablePlaceholderWidget as the canvas renderer for 'subTable' type
FcDesigner.component('subTable', SubTablePlaceholderWidget)

// Register custom field components so form-create can render them in canvas & preview
FcDesigner.component('editor', FcEditor)
FcDesigner.component('transfer', FcTransfer)
FcDesigner.component('cascader', FcCascader)
FcDesigner.component('slider', FcSlider)
FcDesigner.component('lookup', LookupComponent)
FcDesigner.component('LookupBindingSelect', LookupBindingSelect)

// Register the subTable drag rule so it appears in the designer left menu
FcDesigner.addDragRule({
  name: 'subTable',
  label: 'Sub-Table',
  icon: 'icon-table',
  menu: 'main',
  mask: true,
  input: false,
  drag: false,
  dragBtn: true,
  inside: false,
  only: false,
  handleBtn: true,
  languageKey: [],
  // When loading a saved rule, copy top-level _bindingId into props so the config panel can read it
  loadRule(rule: any) {
    rule.props = rule.props || {}
    if (rule._bindingId !== undefined) {
      rule.props._bindingId = rule._bindingId
    }
  },
  // When saving/exporting, move props._bindingId back to top-level _bindingId
  parseRule(rule: any) {
    if (rule.props && rule.props._bindingId !== undefined) {
      rule._bindingId = rule.props._bindingId
      delete rule.props._bindingId
    } else {
      // Ensure _bindingId exists even if props was empty
      if (rule._bindingId === undefined) rule._bindingId = null
    }
  },
  // Keep top-level _bindingId in sync when the props panel changes props._bindingId
  watch: {
    _bindingId({ value, rule }: { value: any; rule: any }) {
      rule._bindingId = value ?? null
    }
  },
  rule() {
    return {
      type: 'subTable',
      _bindingId: null,
      title: 'Sub-Table',
      props: { _bindingId: null }
    }
  },
  props() {
    return [
      {
        type: 'SubTableBindingSelect',
        field: '_bindingId',
        title: 'Sub Table Binding',
        props: {}
      }
    ]
  }
})

// ─── Register custom drag rules for new field types ──────────────────────────
// These allow fc-designer to recognise and render editor/signature/transfer/cascader/slider
// in the canvas and left-side menu.

FcDesigner.addDragRule({
  name: 'editor',
  label: 'Editor',
  icon: 'icon-editor',
  menu: 'main',
  mask: false,
  input: true,
  drag: false,
  dragBtn: true,
  inside: false,
  only: false,
  handleBtn: true,
  languageKey: [],
  rule() {
    return {
      type: 'editor',
      field: 'editor',
      title: 'Editor',
      props: { rows: 5, placeholder: 'Please input content' }
    }
  },
  props() {
    return [
      { type: 'inputNumber', field: 'rows', title: 'Rows', props: { min: 2, max: 20 } },
      { type: 'input', field: 'placeholder', title: 'Placeholder' },
      { type: 'inputNumber', field: 'maxlength', title: 'Max Length' }
    ]
  }
})

FcDesigner.addDragRule({
  name: 'transfer',
  label: 'Transfer',
  icon: 'icon-transfer',
  menu: 'main',
  mask: false,
  input: true,
  drag: false,
  dragBtn: true,
  inside: false,
  only: false,
  handleBtn: true,
  languageKey: [],
  rule() {
    return {
      type: 'transfer',
      field: 'transfer',
      title: 'Transfer',
      props: { options: [], leftTitle: 'Source', rightTitle: 'Target' }
    }
  },
  props() {
    return [
      { type: 'input', field: 'leftTitle', title: 'Left Title' },
      { type: 'input', field: 'rightTitle', title: 'Right Title' }
    ]
  }
})

FcDesigner.addDragRule({
  name: 'cascader',
  label: 'Cascader',
  icon: 'icon-cascader',
  menu: 'main',
  mask: false,
  input: true,
  drag: false,
  dragBtn: true,
  inside: false,
  only: false,
  handleBtn: true,
  languageKey: [],
  rule() {
    return {
      type: 'cascader',
      field: 'cascader',
      title: 'Cascader',
      props: { options: [], placeholder: 'Please select' }
    }
  },
  props() {
    return [
      { type: 'input', field: 'placeholder', title: 'Placeholder' }
    ]
  }
})

FcDesigner.addDragRule({
  name: 'slider',
  label: 'Slider',
  icon: 'icon-slider',
  menu: 'main',
  mask: false,
  input: true,
  drag: false,
  dragBtn: true,
  inside: false,
  only: false,
  handleBtn: true,
  languageKey: [],
  rule() {
    return {
      type: 'slider',
      field: 'slider',
      title: 'Slider',
      props: { min: 0, max: 100, step: 1 }
    }
  },
  props() {
    return [
      { type: 'inputNumber', field: 'min', title: 'Min' },
      { type: 'inputNumber', field: 'max', title: 'Max' },
      { type: 'inputNumber', field: 'step', title: 'Step' }
    ]
  }
})

FcDesigner.addDragRule({
  name: 'lookup',
  label: 'Lookup',
  icon: 'icon-select',
  menu: 'main',
  mask: false,
  input: true,
  drag: false,
  dragBtn: true,
  inside: false,
  only: false,
  handleBtn: true,
  languageKey: [],
  loadRule(rule: any) {
    rule.wrap = rule.wrap || {}
    rule.wrap.class = rule.wrap.class || 'fc-lookup-wrap'
  },
  rule() {
    return {
      type: 'lookup',
      field: 'lookup',
      title: 'Lookup',
      wrap: {
        class: 'fc-lookup-wrap'
      },
      prefix: {
        type: 'i',
        class: 'el-icon',
        style: 'margin-right:4px;font-size:14px;color:#409eff;',
        children: [{
          type: 'svg',
          attrs: {
            viewBox: '0 0 1024 1024',
            width: '1em',
            height: '1em',
            fill: 'currentColor'
          },
          children: [{
            type: 'path',
            attrs: {
              d: 'M909.6 854.5L649.9 594.8C690.2 542.7 714 478.4 714 408c0-167.4-135.6-303-303-303S108 240.6 108 408s135.6 303 303 303c70.4 0 134.7-23.8 186.8-64.1l259.7 259.6c6.2 6.2 16.4 6.2 22.6 0l29.5-29.5c6.3-6.2 6.3-16.4 0-22.5zM411 680c-150.1 0-272-121.9-272-272s121.9-272 272-272 272 121.9 272 272-121.9 272-272 272z'
            }
          }]
        }]
      },
      props: { placeholder: 'Click to search', lookupConfig: '{}' }
    }
  },
  props() {
    return [
      { type: 'input', field: 'placeholder', title: 'Placeholder' },
      { type: 'LookupBindingSelect', field: 'lookupConfig', title: 'Lookup Config', props: {} }
    ]
  }
})

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

  /* Lookup field label icon */
  .fc-lookup-wrap > .el-form-item__label > .fc-form-title::before,
  .fc-lookup-wrap > label > .fc-form-title::before,
  .fc-lookup-wrap .el-form-item__label::before {
    content: '';
    display: inline-block;
    width: 14px;
    height: 14px;
    margin-right: 4px;
    vertical-align: middle;
    background-color: #409eff;
    -webkit-mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1024 1024'%3E%3Cpath d='M909.6 854.5L649.9 594.8C690.2 542.7 714 478.4 714 408c0-167.4-135.6-303-303-303S108 240.6 108 408s135.6 303 303 303c70.4 0 134.7-23.8 186.8-64.1l259.7 259.6c6.2 6.2 16.4 6.2 22.6 0l29.5-29.5c6.3-6.2 6.3-16.4 0-22.5zM411 680c-150.1 0-272-121.9-272-272s121.9-272 272-272 272 121.9 272 272-121.9 272-272 272z'/%3E%3C/svg%3E") no-repeat center / contain;
    mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1024 1024'%3E%3Cpath d='M909.6 854.5L649.9 594.8C690.2 542.7 714 478.4 714 408c0-167.4-135.6-303-303-303S108 240.6 108 408s135.6 303 303 303c70.4 0 134.7-23.8 186.8-64.1l259.7 259.6c6.2 6.2 16.4 6.2 22.6 0l29.5-29.5c6.3-6.2 6.3-16.4 0-22.5zM411 680c-150.1 0-272-121.9-272-272s121.9-272 272-272 272 121.9 272 272-121.9 272-272 272z'/%3E%3C/svg%3E") no-repeat center / contain;
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
