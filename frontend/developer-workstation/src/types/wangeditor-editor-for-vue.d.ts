import type { DefineComponent } from 'vue'

/**
 * `@wangeditor/editor-for-vue` 的类型补声明。
 *
 * 该包自带声明，但外部工程拿不到，有两层原因：
 *
 * 1. 它的 `exports` 只写了 `import` / `require`、没有 `types`：
 *      "exports": { ".": { "import": "./dist/index.esm.js", "require": "./dist/index.js" } }
 *    本项目用 `moduleResolution: "bundler"`，该模式优先按 `exports` 解析，命中 `.js` 后
 *    不再回退看顶层 `types` 字段 —— TS 因此认为这是无声明的 JS 模块（TS7016）。
 *
 * 2. 即便手动转发到它的 `dist/src/index.d.ts` 也没用：那个文件写的是
 *      import Editor from './components/Editor.vue'
 *    包内虽然备了 `Editor.vue.d.ts`，但 `.vue` 说明符要靠使用方的 vue shim 才解析得动，
 *    在外部工程里解析失败，`Editor` / `Toolbar` 就变成「模块没有这个导出」（TS2305）。
 *    ——先前试过转发写法，实测就是撞在这一层。
 *
 * 故这里直接声明两个组件。props 用宽松签名：本项目只在
 * `components/designer/email/EmailRichBodyEditor.vue` 里用它，且全部经 v-model / 事件
 * 交互，不依赖具体 prop 类型；写死一份可能与上游漂移的 props 表反而更容易出错。
 */
declare module '@wangeditor/editor-for-vue' {
  export const Editor: DefineComponent<Record<string, unknown>>
  export const Toolbar: DefineComponent<Record<string, unknown>>
}
