// @form-create/utils 不发布任何 .d.ts（package.json 无 types/typings），
// 深层子路径只能逐个声明。新增子路径 import 时要在这里补一行，否则 TS7016。
declare module '@form-create/utils/lib/type';
declare module '@form-create/utils/lib/deepextend';
declare module '@form-create/utils/lib/json';
