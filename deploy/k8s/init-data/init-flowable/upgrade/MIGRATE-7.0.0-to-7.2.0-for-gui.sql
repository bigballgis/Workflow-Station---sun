-- =============================================================================
-- Flowable 7.0.0 → 7.2.0 数据库迁移（合并脚本，图形客户端可整文件执行）
-- =============================================================================
--
-- 用途：把已在跑 Flowable 7.0.0 的现有库升级到 7.2.0。
--       新建空库**不要**用本文件，请用 ../create/flowable.postgres.all.create.sql。
--
-- 本文件 = 三个官方增量脚本按序合并 + 前后置校验 + 本仓库自有加宽列的保护：
--     flowable.postgres.upgradestep.7.0.0.to.7.0.1.all.sql   → 7.0.1.1
--     flowable.postgres.upgradestep.7.0.1.to.7.1.0.all.sql   → 7.1.0.2
--     flowable.postgres.upgradestep.7.1.0.to.7.2.0.all.sql   → 7.2.0.2
-- 官方原始脚本保留在同目录下，本文件只是把它们串起来并加上守卫，未改动任何 DDL。
--
-- 内容性质：**纯增量** —— 只有 ADD COLUMN / CREATE INDEX / ADD CONSTRAINT，
--           没有 DROP COLUMN，没有列类型变更。已核对：不触碰
--           act_ru_identitylink / act_hi_identitylink / act_hi_comment，
--           因此本仓库在 deploy/init-scripts/00-schema/30- 与 31- 做的
--           varchar(255)→varchar(4000) 加宽**不受影响**（末尾会断言）。
--
-- 例外：7.0.1→7.1.0 会 DROP 8 张 Liquibase changelog 表
--       （ACT_APP_/ACT_CMMN_/ACT_DMN_DATABASECHANGELOG(LOCK)、FLW_EV_...）。
--       这是官方行为：7.1.0 起这些引擎改用 ACT_GE_PROPERTY 记版本，不再用 Liquibase。
--
-- -----------------------------------------------------------------------------
-- 执行前必读
-- -----------------------------------------------------------------------------
-- 1. **先备份整库**（不是只备份 act_*）。act_* 与 dw_* / sys_* 同库，且
--    sys_function_unit_contents.flowable_deployment_id /
--    flowable_process_definition_id 是**没有外键约束**的指针，指向
--    ACT_RE_DEPLOYMENT / ACT_RE_PROCDEF —— 只恢复一部分会静默产生悬垂引用。
--        pg_dump -U <user> -d <db> > backup-pre-flowable72.sql
-- 2. **停掉 workflow-engine**（或缩到 0 副本）后再执行，避免引擎同时自动迁移。
-- 3. 本文件**单向**，官方不提供降级脚本。回滚 = 用上面的备份整库恢复。
-- 4. 幂等性：官方增量脚本**不可重复执行**（重复 ADD COLUMN 会报错）。
--    开头的前置校验会在版本不是 7.0.0.0 时直接中止，请勿绕过。
--
-- -----------------------------------------------------------------------------
-- 各环境适用性：**sit / preprod / uat / prod 需要手工执行本文件**
-- -----------------------------------------------------------------------------
-- 2026-08 起这四个环境的 FLOWABLE_SCHEMA_UPDATE 为 false。引擎**不会**自动迁移，
-- 版本不符会直接启动失败。发布新引擎版本前必须：停引擎 → 整库备份 → 执行本文件
-- → 再启动引擎。
--
-- 两个例外，都是「库本身可丢弃」，不需要跑本文件：
--   dev（docker-compose.dev.yml 显式设 true）：本地库经常被整卷删掉重建，空库启动
--     时若不自动建表，引擎会因 relation "act_ge_property" does not exist 起不来。
--   单元测试（application-test.yml 保持 true）：每次跑都是全新的内存 H2。
--
-- 已验证：本机 dev 库（PostgreSQL 16.5）跑完本迁移后 schema.version=7.2.0.2、
--         19 个加宽列全部保持、存量流程数据完好；同一份备份恢复到临时库后用本
--         文件手工迁移，与引擎自动迁移产出的 act_*/flw_* schema 841 列逐字节一致。
-- =============================================================================


-- =============================================================================
-- 前置校验：当前必须正好是 7.0.0.0，否则中止
-- =============================================================================
DO $precheck$
DECLARE
    v text;
BEGIN
    SELECT value_ INTO v FROM act_ge_property WHERE name_ = 'schema.version';

    IF v IS NULL THEN
        RAISE EXCEPTION 'act_ge_property 中没有 schema.version —— 这不是一个已初始化的 Flowable 库。'
                        '新建库请改用 ../create/flowable.postgres.all.create.sql。';
    END IF;

    IF v = '7.2.0.2' THEN
        RAISE EXCEPTION '已经是 7.2.0.2，无需迁移（本脚本不可重复执行）。';
    END IF;

    IF v <> '7.0.0.0' THEN
        RAISE EXCEPTION '期望 schema.version = 7.0.0.0，实际是 %。'
                        '本脚本只覆盖 7.0.0.0 → 7.2.0.2 这一段。', v;
    END IF;

    RAISE NOTICE '前置校验通过：当前 schema.version = %，开始迁移。', v;
END
$precheck$;


-- =============================================================================
-- 第 1 步：7.0.0 → 7.0.1   （官方 flowable.postgres.upgradestep.7.0.0.to.7.0.1.all.sql）
-- 新增任务生命周期列（STATE_ / CLAIMED_BY_ / IN_PROGRESS_* / SUSPENDED_* 等）
-- =============================================================================
alter table ACT_RU_EVENT_SUBSCR add column SCOPE_DEFINITION_KEY_ varchar(255);

update ACT_GE_PROPERTY set VALUE_ = '7.0.1.1' where NAME_ = 'common.schema.version';
update ACT_GE_PROPERTY set VALUE_ = '7.0.1.1' where NAME_ = 'entitylink.schema.version';
update ACT_GE_PROPERTY set VALUE_ = '7.0.1.1' where NAME_ = 'identitylink.schema.version';
update ACT_GE_PROPERTY set VALUE_ = '7.0.1.1' where NAME_ = 'job.schema.version';
update ACT_GE_PROPERTY set VALUE_ = '7.0.1.1' where NAME_ = 'batch.schema.version';

alter table ACT_RU_TASK
    add column STATE_ varchar(255),
    add column IN_PROGRESS_TIME_ timestamp,
    add column IN_PROGRESS_STARTED_BY_ varchar(255),
    add column CLAIMED_BY_ varchar(255),
    add column SUSPENDED_TIME_ timestamp,
    add column SUSPENDED_BY_ varchar(255),
    add column IN_PROGRESS_DUE_DATE_ timestamp;

alter table ACT_HI_TASKINST
    add column STATE_ varchar(255),
    add column IN_PROGRESS_TIME_ timestamp,
    add column IN_PROGRESS_STARTED_BY_ varchar(255),
    add column CLAIMED_BY_ varchar(255),
    add column SUSPENDED_TIME_ timestamp,
    add column SUSPENDED_BY_ varchar(255),
    add column COMPLETED_BY_ varchar(255),
    add column IN_PROGRESS_DUE_DATE_ timestamp;

update ACT_GE_PROPERTY set VALUE_ = '7.0.1.1' where NAME_ = 'task.schema.version';
update ACT_GE_PROPERTY set VALUE_ = '7.0.1.1' where NAME_ = 'variable.schema.version';
update ACT_GE_PROPERTY set VALUE_ = '7.0.1.1' where NAME_ = 'schema.version';
update ACT_ID_PROPERTY set VALUE_ = '7.0.1.1' where NAME_ = 'schema.version';


-- =============================================================================
-- 第 2 步：7.0.1 → 7.1.0   （官方 flowable.postgres.upgradestep.7.0.1.to.7.1.0.all.sql）
-- 各子引擎从 Liquibase 改为 ACT_GE_PROPERTY 记版本，并删除 changelog 表
-- =============================================================================
create index ACT_IDX_ACT_HI_TSK_LOG_TASK on ACT_HI_TSK_LOG(TASK_ID_);

delete from ACT_GE_PROPERTY where NAME_ = 'batch.schema.version';
delete from ACT_GE_PROPERTY where NAME_ = 'entitylink.schema.version';
delete from ACT_GE_PROPERTY where NAME_ = 'eventsubscription.schema.version';
delete from ACT_GE_PROPERTY where NAME_ = 'identitylink.schema.version';
delete from ACT_GE_PROPERTY where NAME_ = 'job.schema.version';
delete from ACT_GE_PROPERTY where NAME_ = 'task.schema.version';
delete from ACT_GE_PROPERTY where NAME_ = 'variable.schema.version';

create index ACT_IDX_EVENT_SUBSCR_PROC_ID on ACT_RU_EVENT_SUBSCR(PROC_INST_ID_);

update ACT_GE_PROPERTY set VALUE_ = '7.1.0.2' where NAME_ = 'common.schema.version';

insert into ACT_GE_PROPERTY values ('app.schema.version', '7.1.0.2', 1);
drop table ACT_APP_DATABASECHANGELOG;
drop table ACT_APP_DATABASECHANGELOGLOCK;

insert into ACT_GE_PROPERTY values ('cmmn.schema.version', '7.1.0.2', 1);
drop table ACT_CMMN_DATABASECHANGELOG;
drop table ACT_CMMN_DATABASECHANGELOGLOCK;

insert into ACT_GE_PROPERTY values ('dmn.schema.version', '7.1.0.2', 1);
drop table ACT_DMN_DATABASECHANGELOG;
drop table ACT_DMN_DATABASECHANGELOGLOCK;

insert into ACT_GE_PROPERTY values ('eventregistry.schema.version', '7.1.0.2', 1);
drop table FLW_EV_DATABASECHANGELOG;
drop table FLW_EV_DATABASECHANGELOGLOCK;

update ACT_GE_PROPERTY set VALUE_ = '7.1.0.2' where NAME_ = 'schema.version';
update ACT_ID_PROPERTY set VALUE_ = '7.1.0.2' where NAME_ = 'schema.version';


-- =============================================================================
-- 第 3 步：7.1.0 → 7.2.0   （官方 flowable.postgres.upgradestep.7.1.0.to.7.2.0.all.sql）
-- COMPLETED_BY_ 审计列；DMN / EventRegistry 补外键与索引
-- =============================================================================
alter table ACT_RU_ACTINST add column COMPLETED_BY_ varchar(255);
alter table ACT_HI_ACTINST add column COMPLETED_BY_ varchar(255);

ALTER TABLE ACT_CMMN_RU_PLAN_ITEM_INST ADD ASSIGNEE_ VARCHAR(255);
ALTER TABLE ACT_CMMN_RU_PLAN_ITEM_INST ADD COMPLETED_BY_ VARCHAR(255);

ALTER TABLE ACT_CMMN_HI_PLAN_ITEM_INST ADD ASSIGNEE_ VARCHAR(255);
ALTER TABLE ACT_CMMN_HI_PLAN_ITEM_INST ADD COMPLETED_BY_ VARCHAR(255);

ALTER TABLE ACT_DMN_DEPLOYMENT_RESOURCE
    ADD CONSTRAINT ACT_FK_DMN_RSRC_DPL FOREIGN KEY (DEPLOYMENT_ID_) REFERENCES ACT_DMN_DEPLOYMENT (ID_);
CREATE INDEX ACT_IDX_DMN_RSRC_DPL ON ACT_DMN_DEPLOYMENT_RESOURCE (DEPLOYMENT_ID_);

ALTER TABLE FLW_EVENT_RESOURCE
    ADD CONSTRAINT FLW_FK_EVENT_RSRC_DPL FOREIGN KEY (DEPLOYMENT_ID_) REFERENCES FLW_EVENT_DEPLOYMENT (ID_);
CREATE INDEX FLW_IDX_EVENT_RSRC_DPL ON FLW_EVENT_RESOURCE (DEPLOYMENT_ID_);

update ACT_GE_PROPERTY set VALUE_ = '7.2.0.2' where NAME_ = 'common.schema.version';
update ACT_GE_PROPERTY set VALUE_ = '7.2.0.2' where NAME_ = 'schema.version';
update ACT_GE_PROPERTY set VALUE_ = '7.2.0.2' where NAME_ = 'app.schema.version';
update ACT_GE_PROPERTY set VALUE_ = '7.2.0.2' where NAME_ = 'cmmn.schema.version';
update ACT_GE_PROPERTY set VALUE_ = '7.2.0.2' where NAME_ = 'dmn.schema.version';
update ACT_GE_PROPERTY set VALUE_ = '7.2.0.2' where NAME_ = 'eventregistry.schema.version';
update ACT_ID_PROPERTY set VALUE_ = '7.2.0.2' where NAME_ = 'schema.version';


-- =============================================================================
-- 后置校验一：版本号必须为 7.2.0.2
-- =============================================================================
DO $postcheck_version$
DECLARE
    v text;
BEGIN
    SELECT value_ INTO v FROM act_ge_property WHERE name_ = 'schema.version';
    IF v <> '7.2.0.2' THEN
        RAISE EXCEPTION '迁移后 schema.version = %，期望 7.2.0.2。迁移未完成，请回滚。', v;
    END IF;
    RAISE NOTICE '版本校验通过：schema.version = %', v;
END
$postcheck_version$;


-- =============================================================================
-- 后置校验二：本仓库自有的 19 个加宽列必须仍然是加宽状态
--
-- 这是本仓库偏离 stock Flowable DDL 的地方（init-scripts/00-schema/30- 与 31-）：
-- 虚拟组 id / scope id 会超过 stock 的 varchar(255)，被改窄就会在**任务完成时**
-- 报 "value too long for type character varying(255)"，且只在运行时暴露。
-- 官方 7.x 脚本经核对不触碰这些列，此处仅作兜底断言。
-- =============================================================================
DO $postcheck_widened$
DECLARE
    bad text;
BEGIN
    SELECT string_agg(table_name || '.' || column_name || '=' ||
                      data_type || coalesce('(' || character_maximum_length || ')', ''),
                      ', ' ORDER BY table_name, column_name)
    INTO bad
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND (
            (table_name IN ('act_ru_identitylink', 'act_hi_identitylink')
             AND column_name IN ('group_id_', 'type_', 'user_id_', 'scope_id_',
                                 'sub_scope_id_', 'scope_type_', 'scope_definition_id_')
             AND character_maximum_length IS DISTINCT FROM 4000)
         OR (table_name = 'act_hi_comment' AND column_name = 'message_'
             AND data_type <> 'text')
         OR (table_name = 'act_hi_comment' AND column_name IN ('action_', 'type_', 'user_id_')
             AND character_maximum_length IS DISTINCT FROM 4000)
         OR (table_name = 'act_hi_comment' AND column_name = 'full_msg_'
             AND data_type <> 'bytea')
          );

    IF bad IS NOT NULL THEN
        RAISE EXCEPTION E'以下列已不是加宽状态，任务完成会在运行时失败：\n  %\n'
                        '修复：重新执行 deploy/init-scripts/00-schema/30- 与 31- 两个加宽脚本。', bad;
    END IF;

    RAISE NOTICE '加宽列校验通过：19 列全部保持 varchar(4000) / text / bytea。';
    RAISE NOTICE '迁移完成。现在可以启动 Flowable 7.2.0 的 workflow-engine。';
END
$postcheck_widened$;
