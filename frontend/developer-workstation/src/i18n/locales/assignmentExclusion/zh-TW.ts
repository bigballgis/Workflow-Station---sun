export default {
  title: '分派互斥', guide: '分派互斥設定指南',
  initiator: '排除本單原始發起人', previous: '排除本單之前的經辦人', selected: '排除指定節點的經辦人', nodes: '目前功能單元的節點',
  rulesHint: '多條規則合併生效。重新開啟後仍保留各輪的有效經辦責任，不改變檢視權限。',
  backup: '啟用備用候選人', source: '備用來源', buRole: '業務單元與角色', users: '指定使用者',
  buSource: '業務單元來源', startBu: '本單原始發起時的業務單元', nodeBu: '目前節點的分派業務單元', fixedBu: '固定業務單元',
  businessUnit: '業務單元', roles: '角色',
  backupHint: '僅當原候選池非空且全部被互斥規則排除時啟用備用。備用人員也受相同規則約束，並需領取任務。',
  invalid: '目前節點的互斥設定無效，請透過復原恢復，或明確重設設定。', reset: '重設互斥設定',
  selectRule: '請先啟用至少一條互斥規則，再設定備用候選人。', saveBlocked: '請完善這些節點的互斥設定：{nodes}',
  selectNodes: '請選擇現有使用者任務節點，並移除已刪除節點的引用。', selectUsers: '請至少選擇一名備用使用者。',
  selectBu: '請選擇固定業務單元。', selectRoles: '請至少選擇一個備用角色。',
  invalidNodeBu: '目前分派來源無法確定唯一業務單元，請選擇其他業務單元來源。', catalogError: '無法載入分派選項，請重新搜尋或重新開啟面板。'
}
