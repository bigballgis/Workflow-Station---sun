-- P0-1 查询性能：为 rt_table_data_rows 的 JSON 行存储加索引（不迁数据，仅加索引）
--
-- 背景：行数据搜索为 `data->>'任意字段' ILIKE '%term%'`，字段名由用户动态定义，
-- 无法为每个字段建表达式索引。通用解法是在整行 JSON 文本 (data::text) 上建 pg_trgm GIN 索引，
-- 配合查询侧改写为 `data::text ILIKE '%term%' AND (逐字段精确过滤)`，
-- 让 leading-wildcard 模糊匹配走索引、逐字段过滤保证语义正确。
--
-- 写放大说明：GIN trgm 索引会增大写入成本与索引体积，属可接受代价；
-- 仅在"按字段 ILIKE 搜索大表"场景受益，对纯 table_id 分页无影响。

-- 1) trigram 扩展（与 workflow-engine V500 同样用 IF NOT EXISTS，DB role 已具扩展权限）
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2) 整行 JSON 文本的 trgm GIN 索引：加速 `data::text ILIKE '%term%'`（任意字段、任意表通用）
CREATE INDEX IF NOT EXISTS idx_rt_data_rows_data_trgm
    ON rt_table_data_rows USING gin ((data::text) gin_trgm_ops);

-- 3) 分页复合索引：查询恒为 `WHERE table_id = ? ORDER BY id`，
--    (table_id, id) 让该排序分页走索引、避免大表下的额外排序
CREATE INDEX IF NOT EXISTS idx_rt_data_rows_table_id_id
    ON rt_table_data_rows (table_id, id);

COMMENT ON INDEX idx_rt_data_rows_data_trgm IS
    'pg_trgm GIN on whole-row JSON text; serves `data::text ILIKE ''%term%''` for dynamic-field search';
COMMENT ON INDEX idx_rt_data_rows_table_id_id IS
    'Supports `WHERE table_id = ? ORDER BY id LIMIT/OFFSET` pagination without extra sort';

-- 大表运维提示（非本迁移执行，供 DBA 参考）：
-- 若 rt_table_data_rows 已有大量数据、需避免建索引期间锁写，可在迁移外手动执行：
--   CREATE INDEX CONCURRENTLY ... （不能在 Flyway 事务内执行）
-- 本迁移用普通 CREATE INDEX，保证 Flyway 事务一致性；中小表锁时间可忽略。
