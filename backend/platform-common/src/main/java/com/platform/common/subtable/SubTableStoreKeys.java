package com.platform.common.subtable;

import java.util.Locale;
import java.util.Map;

/**
 * {@code __subTables__} 单一真相源 — key 规则（后端侧，与前端 {@code subTableStore.ts} 一一对应）。
 *
 * <p><b>为什么存在。</b>历史结构把同一张表的行按「每个 binding 一份 + 每个名字别名一份」存进
 * {@code __subTables__}：FU 50005 的 {@code subtable} 表被 6 张表单绑定，加上 {@code subtable} /
 * {@code Participants} / {@code participants} 三个别名，同一批行存了 9 份。不同写入路径各写一部分
 * key，副本随即分叉（实测 {@code 50539} 有 2 行而 {@code 50544} 只有 1 行），后端再逐 key 处理，
 * 用户的修改会被另一份旧副本盖掉。
 *
 * <p><b>规则：一张表一个 key。</b>binding 不是数据身份，只是「表单 ↔ 表」的连接；数据身份只能是
 * 表本身。
 *
 * <p><b>为什么用 name 而不是 id。</b>表 id 是自增主键，FU clone（{@code FunctionUnitCloner} 的
 * {@code tableIdMapping}）、导入导出、部署到新环境都会重新映射，用 id 做 key 的数据会指向错误的
 * 表。表名在这些操作中保持不变，且唯一性由数据库唯一索引强制：{@code uk_dw_table_name} /
 * {@code ux_dw_table_name_lower}、{@code rt_table_definitions_table_name_key} /
 * {@code ux_rt_table_name_lower}（均含 {@code lower()} 版本，故本类统一小写归一）。
 *
 * <p><b>为什么仍要前缀。</b>DW 表与 RT 表是两张独立定义表、各自唯一，跨表没有联合约束，理论上
 * 可以同名（实测 id 已经撞过 2 个）。{@code dw:} / {@code rt:} 两个命名空间保证不会互相覆盖。
 *
 * <p><b>只认规范 key。</b>前后端同时上线，不存在版本错配窗口，因此不做「新旧格式都认」的兼容层
 * ——名字兜底恰恰是旧结构产生分叉的原因之一。
 */
public final class SubTableStoreKeys {

    public static final String DW_PREFIX = "dw:";
    public static final String RT_PREFIX = "rt:";

    private SubTableStoreKeys() {
    }

    /** 与数据库 {@code lower(table_name)} 唯一索引对齐的归一化。 */
    public static String normalizeTableName(Object name) {
        return name == null ? "" : String.valueOf(name).trim().toLowerCase(Locale.ROOT);
    }

    /** DW 设计器表 → {@code dw:<name>}；表名为空时返回 {@code null}（不猜）。 */
    public static String dwKey(Object tableName) {
        String n = normalizeTableName(tableName);
        return n.isEmpty() ? null : DW_PREFIX + n;
    }

    /** RT 关联表（含平台虚拟表 {@code sys_users}）→ {@code rt:<name>}。 */
    public static String rtKey(Object relationTableName) {
        String n = normalizeTableName(relationTableName);
        return n.isEmpty() ? null : RT_PREFIX + n;
    }

    /**
     * 由「绑的是 DW 表还是 RT 表」决定命名空间。
     *
     * <p>判定无歧义：{@code dw_form_table_bindings} 实测 70 条只有 {@code table_id}、31 条只有
     * {@code relation_table_id}，两者都有 / 都无的均为 0 条。
     *
     * @param relationTable 非 {@code null} 即视为 RT binding
     * @return 规范 key，或 {@code null}（表名无法解析）
     */
    public static String storeKey(Object dwTableName, Object relationTableName, boolean isRelationBinding) {
        if (isRelationBinding) {
            String rt = rtKey(relationTableName != null ? relationTableName : dwTableName);
            return rt;
        }
        return dwKey(dwTableName);
    }

    /** key 是否为规范格式（用于断言 / 诊断，不用于回退）。 */
    public static boolean isCanonical(Object key) {
        String k = key == null ? "" : String.valueOf(key);
        return k.startsWith(DW_PREFIX) || k.startsWith(RT_PREFIX);
    }

    /**
     * 规范 key 里携带的表名，非规范 key 返回 {@code null}。
     *
     * <p>规范 key 自带表名，调用方因此不再需要「binding id → 表名」的查表，也不会被显示名别名
     * （{@code Participants} 之类）误导。
     */
    public static String tableNameOf(Object key) {
        String k = key == null ? "" : String.valueOf(key);
        if (k.startsWith(DW_PREFIX)) {
            return k.substring(DW_PREFIX.length());
        }
        if (k.startsWith(RT_PREFIX)) {
            return k.substring(RT_PREFIX.length());
        }
        return null;
    }

    /**
     * 从 {@code __subTables__} 取某张表的行。
     *
     * <p>只按规范 key 取，不做 binding id / 表名 / 显示名的兜底 —— 那正是旧结构分叉的来源。
     *
     * @return 行数组；key 不存在或值不是数组时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public static java.util.List<Object> readRows(Map<String, Object> subTables, String storeKey) {
        if (subTables == null || storeKey == null) {
            return null;
        }
        Object v = subTables.get(storeKey);
        return v instanceof java.util.List ? (java.util.List<Object>) v : null;
    }

    /** 写入：只写规范 key，绝不扇出别名。 */
    public static boolean writeRows(Map<String, Object> subTables, String storeKey, Object rows) {
        if (subTables == null || storeKey == null) {
            return false;
        }
        subTables.put(storeKey, rows);
        return true;
    }
}
