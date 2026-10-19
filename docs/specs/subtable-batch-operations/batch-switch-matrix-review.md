# Code Review：A03 批量开关组件组合验收

## 结论

最终独立 staged 重审无新增 Blocker/Major，本测试窄批可 CONDITIONAL 提交。本批仅增加测试，不改产品代码；A03 组件层组合验收与完整服务端/浏览器验收分开计。A39 #1666 按用户要求继续暂缓，P4 仍不放行。

## Findings

Blocker/Major/Minor/Question：自审及独立重审无新增产品 finding。首次测试装配遗漏 vue-i18n 的 createI18n，模块收集失败；改为保留原模块、只替换 useI18n。未修改产品实现来迎合测试。根组件的 Element Plus 组件及 loading 指令显式 stub，不关闭 Vue 错误/警告。重审建议缺省CRUD分支也显式检查基线，已补断言。

## 范围 / 验收映射

- 测试真实 SubTableField 的 setup、computed、工具栏渲染、点击入口及传给工作区的 props；只 stub 其子组件和外部依赖。不在测试内复制产品 canBatch* 公式作为被测函数。
- 16批量开关组合 × 8原CRUD组合，共128场景；故意将 live allowAdd/Edit/Delete 设为冻结值的反值，确认批量设计资格不读取 live CRUD。
- 原CRUD缺省的16场景，保持原允许语义；Assign 受原Edit约束，而不依赖 Batch Edit 开关。
- 每种批量组合各覆盖 whole-form readonly、ACTION、错误 binding，共48拒绝场景。
- 旧无配置及四开关全关为1场景2次挂载：无批量工作区挂载，原 Export/Import/Add 顺序不变。
- 合计193测试；可用入口在原按钮左侧，点击打开的工作区四项命令与冻结权限一致；打开不 emit 行更新、不调用 onBatchChange，基线数据不变。

## 红→绿有效性

在最初177场景版本，临时将真实 canBatchWorkspace 改成仅 canBatchEdit：同文件51失败、126通过；恢复原来的四项 OR 后177全绿。突变只用于测试，不部署，产品文件恢复与 HEAD 完全一致。随后补16项原CRUD缺省正例，最终组合结果见下。

## 编译 / 测试 / 静态检查

- 初始相关3文件201测试通过（矩阵177+已有24）；补缺省场景后最终3文件217通过（矩阵193+已有24）；独立 reviewer 复跑193通过。
- vue-tsc 383诊断，与上批基线一致；本测试文件新增诊断0，不宣称全量类型检查 PASS。
- Portal build 27.04s通过，既有chunk-size提示未隐藏。
- cached diffcheck通过；独立 reviewer 对116行非空新增行四类敏感模式扫描0命中，无凭证或真实目录PII。

## 触发矩阵 / 生命周期 / 性能

implementation-autonomy、bugfix-verification、code-review、testing。仅本地组件测试及本文，未变更运行能力、字段映射、配置格式、API、i18n、业务数据、鉴权或部署配置；FU Save/Load/Export/Import/Clone/Rollback/Admin 两导入均 N/A 于本批修改。
没有可部署产品变化，无需为测试文件重建 Docker 或重复运行上批已通过的 MI 浏览器回归；不以该 N/A 推断全部生命周期/MI 已验收。测试 mount 使用合成配置，并非实际创建 FU，也不冒充双FU浏览器验证。无性能行为变化或压测结论。

## 未验证 / 剩余门禁

本批不将完整 A03 标 PASS：服务端对伪造请求的128组合、真实浏览器全部16组合、MI参与者创建阶段/运行阶段及多用户权限仍需独立证据。当前测试只证明客户端组件入口和命令投影。A39 暂缓、其他启动/快照写入口、全生命周期/跨库、Preview完整属性和容量验收仍开放。
