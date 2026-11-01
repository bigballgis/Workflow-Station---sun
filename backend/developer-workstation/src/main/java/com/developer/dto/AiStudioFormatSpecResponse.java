package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * IDE 扩展写 {@code generatedData} 前读取的格式说明：当前生效的 GENERATION 提示词 + 枚举与结构元数据。
 *
 * <p>两者都是 AI Generate 喂给模型的同一份内容，扩展不内置格式，DW 升级不需要扩展发版。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStudioFormatSpecResponse {

    /** 当前环境生效的 GENERATION 提示词（含 generatedData 字段规范）。 */
    private String template;

    /** 枚举与结构元数据；与 template 里的枚举冲突时以它为准。 */
    private Map<String, Object> schemaMetadata;
}
