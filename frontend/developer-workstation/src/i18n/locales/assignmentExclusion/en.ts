export default {
  title: 'Assignment exclusions', guide: 'Assignment exclusion guide',
  initiator: 'Exclude the original initiator', previous: 'Exclude previous handlers of this request',
  selected: 'Exclude handlers at selected nodes', nodes: 'Nodes in this function unit',
  rulesHint: 'Rules combine. Effective handling responsibility is retained across reopened rounds. Viewing access is unchanged.',
  backup: 'Use backup candidates', source: 'Backup source', buRole: 'Business unit and roles', users: 'Selected users',
  buSource: 'Business unit source', startBu: 'Business unit at the original start', nodeBu: 'This node’s assignment business unit',
  fixedBu: 'Fixed business unit', businessUnit: 'Business unit', roles: 'Roles',
  backupHint: 'Backup applies only when exclusions remove every member of a nonempty primary pool. Backup candidates still follow the same exclusions and must claim the task.',
  invalid: 'This node has an invalid exclusion configuration. Restore it with Undo, or explicitly reset it.',
  reset: 'Reset exclusions', selectNodes: 'Select existing user-task nodes. Remove references to deleted nodes.',
  selectRule: 'Enable at least one exclusion rule before configuring backup candidates.',
  saveBlocked: 'Complete the exclusion settings for nodes: {nodes}',
  selectUsers: 'Select at least one backup user.', selectBu: 'Select a fixed business unit.', selectRoles: 'Select at least one backup role.',
  invalidNodeBu: 'This assignment source cannot determine one business unit. Choose another BU source.',
  catalogError: 'Assignment options could not load. Retry the search or reopen this panel.'
}
