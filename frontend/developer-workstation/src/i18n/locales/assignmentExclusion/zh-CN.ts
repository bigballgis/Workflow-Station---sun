export default {
  title: '分派互斥', guide: '分派互斥配置指南',
  initiator: '排除本单原始发起人', previous: '排除本单之前的经办人', selected: '排除指定节点的经办人', nodes: '当前功能单元的节点',
  rulesHint: '多条规则合并生效。重新打开后仍保留各轮的有效经办责任，不改变查看权限。',
  backup: '启用备用候选人', source: '备用来源', buRole: '业务单元与角色', users: '指定用户',
  buSource: '业务单元来源', startBu: '本单原始发起时的业务单元', nodeBu: '当前节点的分派业务单元', fixedBu: '固定业务单元',
  businessUnit: '业务单元', roles: '角色',
  backupHint: '仅当原候选池非空且全部被互斥规则排除时启用备用。备用人员也受相同规则约束，并需领取任务。',
  invalid: '当前节点的互斥配置无效，请通过撤销恢复，或明确重置配置。', reset: '重置互斥配置',
  selectRule: '请先启用至少一条互斥规则，再配置备用候选人。', saveBlocked: '请完善这些节点的互斥配置：{nodes}',
  selectNodes: '请选择已有用户任务节点，并移除已删除节点的引用。', selectUsers: '请至少选择一名备用用户。',
  selectBu: '请选择固定业务单元。', selectRoles: '请至少选择一个备用角色。',
  invalidNodeBu: '当前分派来源无法确定唯一业务单元，请选择其他业务单元来源。', catalogError: '无法加载分派选项，请重新搜索或重新打开面板。'
}
