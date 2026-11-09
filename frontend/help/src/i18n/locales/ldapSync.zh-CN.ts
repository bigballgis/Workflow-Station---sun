export default {
  ldapSyncGuide: {
    pageTitle: 'LDAP 同步记录',
    crumb: '管理中心 · 审计日志',
    intro:
      'LDAP 同步记录列出本环境的每一次 LDAP / AD 同步：用户档案和虚拟组名单，含状态、计数和该次同步留下的消息。页面只读。System Administrator、Technical Lead、Auditor 看到的是同一份记录。',
    flowTitle: '操作顺序',
    flow1: '打开管理中心 → 审计日志 → LDAP 同步记录。',
    flow2: '在列头把「状态」筛成「失败」或「部分成功」，或在搜索框输入 AD 组名。',
    flow3: '看「消息」。组同步加了谁、移除了谁，到「管理后台审计日志」查操作人 LDAP_SYNC_JOB。',
    listTitle: '列表显示什么',
    listBody: '每次同步一行，最新的在上。同步开始时写入（进行中），结束时改为成功、部分成功或失败。',
    listLead: '字段目录 — LDAP 同步记录列表（中文界面列名：类型、状态、开始时间、耗时、读取数、新增、更新、失败条数、跳过条数、消息）。',
    listFigure: '以 Technical Lead 打开 LDAP 同步记录（英文界面）：审计日志菜单、搜索 No virtual group、消息列。没有发起同步的按钮。',
    fType:
      '用户（全量）、用户（增量）从目录读取用户档案，只在 System Administrator 发起时执行。增量只读最近一次状态为「成功」的用户同步（全量或增量）之后改过的条目，「部分成功」的用户同步和组同步都不算；还没有这样的记录时按用户（全量）执行并记录。组（全量）、组（增量）把每份虚拟组名单对齐到它映射的 AD 组，两者做的事相同。启动时和按计划执行的同步记为组（增量）。',
    fStatus: '进行中、成功、部分成功或失败（含义见「各状态的含义」）。可在列头筛选。',
    fStarted: '同步开始的时间，按浏览器所在时区显示。',
    fDuration: '本次耗时，如 12.3s、2m 05s。短横：还没有结束，或该类型不记录耗时。',
    fFetched: '本次从目录读取的条目数。',
    fAddedUpdated: '本次按目录新建、更新的用户账号数。短横：还没有结束，或是没有这两个数的旧记录。',
    fFailedItems: '有员工编号、但没能保存的目录条目，例如数据库出错；哪些条目见管理中心日志。同步不会因此中断；对状态的影响见「各状态的含义」里的部分成功。',
    fSkipped:
      '没有员工编号的目录条目。按设计这类账号不入库：不算失败，也不影响状态；「消息」里 Skipped without an employee ID (not stored) 后面是跳过的条数。组同步里一个账号在两个映射的 AD 组中会算两次，与「读取数」一致。短横：没有这个数的旧记录。',
    fMessage:
      '同步因错误中止时：原样记录的错误文本，超过 1000 个字符截断。组同步：Not read, lists unchanged、Read incompletely, nobody removed、No members found (missing or empty AD group) 等说明，后面跟相关的 AD 组，多条之间用分号隔开。读过目录的同步：Skipped without an employee ID (not stored) 后面跟跳过的条数；用户同步：Could not be stored 后面跟没能保存的条数。没有要说明的内容时为空。消息太长时单元格里显示不全，鼠标悬停可看全文，每条说明一行。',
    statusTitle: '各状态的含义',
    statusLead: '字段目录 — 「状态」的取值（中文界面：进行中、成功、部分成功、失败）。',
    sRunning: '同步已开始，还没有结束。',
    sSucceeded:
      '用户同步：目录读完了，有员工编号的条目都保存了。组同步：每个映射的 AD 组都完整读到，有员工编号的成员也都保存了。没有员工编号的条目记为跳过（跳过条数），不影响状态。AD 组为空或不存在（No members found）、为保住 System Administrators 保留了 AD 成员（Kept the AD members of …）、或者没有任何名单映射 AD 组，都仍算成功。',
    sPartial:
      '用户同步：目录读完了，有员工编号的条目有的没能保存（失败条数；「消息」：Could not be stored），其余都保存了。下一次用户（增量）从最近一次「成功」的用户同步开始读，不从这一次开始。组同步：部分映射的 AD 组读不出（Not read, lists unchanged），或只读到一部分、成员没能全部保存（Read incompletely, nobody removed）。这些 AD 组对应的名单不移除任何人；其余 AD 组照常同步。「消息」里列出相关的 AD 组。',
    sFailed:
      '组同步：映射的 AD 组一个都读不出，例如目录连不上；所有名单都没有变化，「消息」在 Not read, lists unchanged 后面列出这些 AD 组。用户同步：目录读完了，但有员工编号的条目一条都没能保存（新增、更新都是 0；「消息」：Could not be stored），下一次用户（增量）从最近一次「成功」的用户同步开始读。任何同步：因错误中止，「消息」里是错误文本。',
    scheduleTitle: '什么时候同步',
    scheduleBody:
      '只有管理中心同时打开 ldap.enabled 和 ldap.sync-enabled 两个开关时才会同步。开关打开后，管理中心启动时执行一次组同步，之后按 ldap.sync-cron 定时执行（默认每 2 小时，整点）。System Administrator 也可以调接口手动发起四种类型中的任意一种，管理中心界面上没有入口。',
    scheduleLock:
      '同一时刻只有一次 LDAP 同步在跑，多个管理中心实例之间也是如此。启动、定时或手动的同步正在进行时，再调接口发起会被拒绝，返回 HTTP 409 和「另一次 LDAP 同步正在进行，请等它结束后再发起。」。启动或定时同步发现另一次同步正在进行时直接跳过，不排队补跑，管理中心日志记一条警告。同步跑多久就持锁多久；同步途中管理中心停了，锁最多 20 分钟后释放。持锁用的 Redis 连不上时，调接口发起会被拒绝，返回 HTTP 503 和「LDAP 同步未启动：同步锁服务（Redis）无法连接。请稍后重试，或请平台运维检查 Redis。」。',
    findTitle: '查找某次同步',
    findLead: '字段目录 — 工具栏和列头。',
    fSearch:
      '中文界面为「搜索类型、状态、消息或组」。匹配「消息」和组同步读取的 AD 组（AD 组不单独成列），也匹配类型、状态的存储代码，如 FAILED、HERMES_AD_GROUP，不匹配中文标签。按回车或点「搜索」；清空搜索框会重新加载。',
    fColumnMenu: '排序、筛选、左右移动或调整列宽。「类型」「状态」按标签筛选。',
    fExport: '「导出」：有勾选时把勾选的行导出为 CSV，没有勾选时导出当前页。',
    fRefresh: '「刷新」：按当前搜索和筛选重新加载。',
    fTotal: '「共 N 条」：符合搜索和筛选的记录数。',
    accessTitle: '谁能打开',
    accessBody: '能打开本页的人看到同一份记录，不按团队过滤。本页没有发起、停止或删除同步的按钮。',
    accessMessage: '「消息」对所有角色原样显示。失败信息里可能有 LDAP 主机名或 DN，不含密码。',
    fRoleAdmin: '看到全部记录。只有 System Administrator 能发起同步，且只能调接口，界面上没有入口。',
    fRoleLead: '看到全部记录，只读。',
    fRoleAuditor: '看到全部记录，只读。',
    membersTitle: '加了谁、移除了谁',
    membersBody:
      '组同步在本页只记计数。它往虚拟组名单里加入或移除的每一个人，都在「管理后台审计日志」里单独一条，操作人为 LDAP_SYNC_JOB。手工加入的成员不会被同步移除。',
    failTitle: '看起来不对时',
    failEmpty: '没有记录：本环境没有打开 LDAP 同步（ldap.enabled 和 ldap.sync-enabled 都要打开），或还没有执行过。关闭之前写下的记录仍留在列表里。',
    failRunning: '开始很久仍是「进行中」：该次同步没有结束，例如执行中管理中心重启了。下一次同步会写自己的记录。',
    failFailed:
      '失败：看「消息」。Not read, lists unchanged 后面列出全部映射的 AD 组，表示目录完全读不出；Could not be stored 且新增、更新都是 0，表示用户同步读完了目录但一条都没能保存，原因见管理中心日志；其他内容是同步中止时的错误。组同步会在下一个计划时间再执行；原因排除后，System Administrator 也可以发起一次。',
    failPartialStatus:
      '部分成功：组同步时，「消息」列出没读出或只读到一部分的 AD 组，它们对应的名单没有移除任何人，下一次同步会再试。用户同步时，有条目没能保存（Could not be stored），哪些条目见管理中心日志；下一次用户（增量）从最近一次「成功」的用户同步重新读。',
    failNotRead: 'Not read, lists unchanged：这些 AD 组读取失败，它们对应的名单保留原有的 AD 成员。本次为部分成功；一个 AD 组都没读出时为失败。',
    failPartial: 'Read incompletely, nobody removed：这些 AD 组只读到一部分，或有成员没能保存。新成员照常加入，但对应的名单里不移除任何人。本次为部分成功。',
    failNoMembers:
      'No members found (missing or empty AD group)：映射的 AD 组不存在或没有成员，它对应的名单会移除原有的 AD 成员。这条说明不影响状态。到「虚拟组管理」核对 AD Group（团队为 Developer AD 组、Technical Lead AD 组）。',
    failKept:
      'Kept the AD members of …: removing them would leave no active member：只针对 System Administrators 组——移除后该组将没有有效成员，本次保留了它的 AD 成员。这条说明不影响状态。其他名单没有这项保护，按 AD 的结果移除。',
    failBusy:
      '调接口发起同步返回 409「另一次 LDAP 同步正在进行…」：启动、定时或手动的同步正在进行，等它结束后再发起。启动或定时同步开始后的 1 分钟内，即使已经结束，手动发起也会被拒绝。',
    failLockUnavailable:
      '调接口发起同步返回 503「LDAP 同步未启动：同步锁服务（Redis）无法连接…」：管理中心连不上存放同步锁的 Redis，本次没有发起任何同步。稍后重试；持续出现时请平台运维检查 Redis。',
    failNoMapping: 'No virtual group maps an AD group：没有任何名单映射 AD 组，本次没有读取，状态为成功。',
    failLoad: '加载 LDAP 同步记录失败：点「刷新」重试。',
    failMenu: '审计日志下没有「LDAP 同步记录」：你的账号不是 System Administrator、Auditor，也不是有效团队的 Technical Lead。',
  },
}
