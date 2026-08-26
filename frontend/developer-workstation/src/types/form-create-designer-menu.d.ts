declare module '@form-create/designer/src/config/menu' {
  interface FormCreateMenuConfig {
    name: string
    title: string
    list: unknown[]
  }
  const menus: FormCreateMenuConfig[]
  export default menus
}
