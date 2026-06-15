package com.developer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 主表 Request ID 配置。
 *
 * <p>由开发者在 Table Design 主表上配置:从主表字段里选取若干字段、定义顺序,用分隔符拼成
 * 一条 request 的人类可读标识(如 {@code dept}-{@code year}-{@code seq} → {@code HR-2026-001})。
 *
 * <p>以 JSONB 存于 {@code dw_table_definitions.request_id_config}。运行时按 {@link #fieldNames}
 * 顺序从流程变量(扁平 {@code fieldName: value})取值,用 {@link #separator} 拼接,空值字段跳过。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RequestIdConfig {

    /** 有序字段名列表;顺序即拼接先后。存 fieldName 而非 id,运行时直接对齐扁平 variables。 */
    private List<String> fieldNames;

    /** 字段间分隔符,如 "-" / "/" / "_" / "." / " " / ""(无分隔符)。 */
    private String separator;
}
