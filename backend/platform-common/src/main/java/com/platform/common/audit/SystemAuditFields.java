package com.platform.common.audit;

import java.util.Locale;
import java.util.Set;

/**
 * Function-unit 表的平台托管审计字段——全平台唯一判定来源。
 *
 * <p>语义为<b>精确四名</b>（大小写不敏感、忽略首尾空白）：{@code created_at} / {@code created_by} /
 * {@code updated_at} / {@code updated_by}。这些字段由 Table Design 自动追加、由 portal 在真正
 * insert/update 时填值、在所有表单解析器中强制只读。
 *
 * <p><b>不做模糊匹配</b>（如 {@code create_time} / {@code createUser} 等变体一律不算）：
 * 审计字段名由平台自己生成、恒为精确四名；模糊匹配会把用户自建的同名业务字段误判为系统字段
 * （设计端可编辑、运行端被锁死/覆盖）。2026-07 存量扫描确认全库无任何模糊变体字段。
 *
 * <p>消费方：DW {@code TableDesignComponentImpl}（设计期自动追加）、DW
 * {@code FormDesignComponentImpl}（校验白名单）、DW {@code TableAuditFieldInitializer}（存量补列）、
 * portal {@code SystemAuditFieldFiller}（运行期填值——与 Form Design 画布解耦，发起/更新时
 * 无条件写入 process variables）。前端对应实现：DW
 * {@code utils/tableAuditFields.ts}、portal {@code subTableAddDialogHelpers/rowInit.ts}
 * ——改动本类语义时须同步两份前端。
 */
public final class SystemAuditFields {

    public static final String CREATED_AT = "created_at";
    public static final String CREATED_BY = "created_by";
    public static final String UPDATED_AT = "updated_at";
    public static final String UPDATED_BY = "updated_by";

    /** 精确四名集合（小写形态）。 */
    public static final Set<String> ALL = Set.of(CREATED_AT, CREATED_BY, UPDATED_AT, UPDATED_BY);

    private SystemAuditFields() {
    }

    /** @return 该字段名是否为平台托管审计字段（精确四名，大小写不敏感、忽略首尾空白）。 */
    public static boolean isAuditField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        return ALL.contains(fieldName.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * {@code created_at} / {@code updated_at} are timestamps. Legacy tables sometimes
     * declared them VARCHAR; the stored value is still an ISO datetime, so a list
     * filter must compare calendar days, not run a text contains.
     */
    public static boolean isTimestamp(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String name = fieldName.trim().toLowerCase(Locale.ROOT);
        return CREATED_AT.equals(name) || UPDATED_AT.equals(name);
    }

    /**
     * {@code created_by} / {@code updated_by} identify a person. Legacy rows may store a
     * display name; the list filter still treats the column as USER so the dialog is a
     * people picker, not a free-text contains.
     */
    public static boolean isUser(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String name = fieldName.trim().toLowerCase(Locale.ROOT);
        return CREATED_BY.equals(name) || UPDATED_BY.equals(name);
    }
}
