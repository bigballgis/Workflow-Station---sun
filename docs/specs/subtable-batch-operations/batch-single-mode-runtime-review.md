# Code Review：Add-only / Delete-only 真实页面验收

## 结论 / 范围

CONDITIONAL；仅增加合成 fixture 模式和运行验证，不改产品代码。本批覆盖 A03/A04 的两种 membership-only 形态，不把它们等同于四开关16种组合或P3/P4全部验收。

## 改动与检查

fixture 创建脚本增加明确 all/add/edit/delete 模式，非法值拒绝；未指定时仍为原 all，原 FIXTURE_EDIT_ONLY=1 仍对应 edit。只为带合成标签的专用FU生成设计。新验证脚本必须显式任务UUID并核对合成FU、TASK表单、单一冻结控件及严格操作布尔；不自动选择业务任务。

## 真实运行

- Add-only：新建FU50032，PROCESS50714/TASK50715，任务 `a44b32c9-c870-11f1-a63d-c6848797bef7`，过程 `a440ac32-c870-11f1-a63d-c6848797bef7`。
- Delete-only：新建FU50033，PROCESS50716/TASK50717，任务 `a99a25ba-c870-11f1-a63d-c6848797bef7`，过程 `a9908883-c870-11f1-a63d-c6848797bef7`。
- 分别执行 `FIXTURE_TASK_ID=... node frontend/scripts/verify-batch-operation-modes.mjs`：两次PASS/pageerror0。Batch Edit均可进入；既有行普通字段没有编辑输入；Add-only只有Add而无Delete，Delete-only反之。Apply前GET与baseline完整相等；Apply/原自动保存后分别3行/1行；传输声明分别只有ADD/DELETE；保留行除合法版本变化外逐字段相等；新增人工PK和FK正确，reload仍能打开。
- 截图绝对路径：`F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-15_batch-add-only.png`、同目录 `2026-10-15_batch-delete-only.png`，保留不删除。图中仅合成值，不含业务姓名或邮箱。

## 未验证 / 兼容 / 安全

产品产物与上一批相同（build/完整MI234+6已通过），本测试/文档批不重复构建服务。脚本语法、diff和增量敏感信息由staged复审确认。未改FU包格式，Export/Import/Clone/Rollback/Admin消费者本批N/A；此处两份均为新建，不冒充导入或多用户验收。没有新增线上环境变量/权限策略。运行只操作明确合成实例，无删除平台卷/历史数据。P2 Assign同工作区、完整分派链、Preview、New Request、启动跨边界/非标准来源、全生命周期、两用户、16组合、窄屏满表与压测及383既有类型错误仍未闭合。
