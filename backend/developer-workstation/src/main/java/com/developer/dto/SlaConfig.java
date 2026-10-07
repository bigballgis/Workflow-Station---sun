package com.developer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 主表 SLA 到期日映射:到期日 = 开始日 + 时效天数(自然日)。
 *
 * <p>以 JSONB 存于 {@code dw_table_definitions.sla_config},只说明"用哪些字段";时效天数是环境数据,
 * 由 Admin Center 按功能单元维护在 {@code ac_sla_policies},不随功能单元导出。
 * 到期日是服务端派生字段:门户在每条写路径上按当前时效天数重算并覆盖客户端传值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SlaConfig {

    public enum StartDateSource {
        /** 开始日取主表字段 {@link #startDateField}(DATE / TIMESTAMP,时间部分忽略)。 */
        FIELD,
        /** 开始日取案件提交时间。 */
        SUBMITTED_AT
    }

    private StartDateSource startDateSource;

    /** {@link StartDateSource#FIELD} 时必填。 */
    private String startDateField;

    /** 写入到期日的主表字段,必须是 DATE、非公式字段。 */
    private String dueDateField;
}
