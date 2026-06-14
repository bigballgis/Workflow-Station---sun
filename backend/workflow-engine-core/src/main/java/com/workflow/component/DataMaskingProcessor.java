package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 数据脱敏与匿名化处理器
 *
 * <p>由 {@link DataAccessSecurityComponent} 拆分而来，承载"数据脱敏和匿名化"职责。
 * 逻辑/异常/日志逐字保留，行为零变化。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataMaskingProcessor {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityManagerComponent securityManagerComponent;

    // 缓存键前缀
    private static final String MASK_RULE_PREFIX = "security:mask_rule:";

    // 内存缓存
    private final Map<String, DataMaskRule> maskRules = new ConcurrentHashMap<>();

    // 敏感数据模式
    private static final Map<String, Pattern> SENSITIVE_PATTERNS = new HashMap<>();

    static {
        SENSITIVE_PATTERNS.put("PHONE", Pattern.compile("1[3-9]\\d{9}"));
        SENSITIVE_PATTERNS.put("ID_CARD", Pattern.compile("\\d{17}[\\dXx]"));
        SENSITIVE_PATTERNS.put("EMAIL", Pattern.compile("[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}"));
        SENSITIVE_PATTERNS.put("BANK_CARD", Pattern.compile("\\d{16,19}"));
        SENSITIVE_PATTERNS.put("PASSWORD", Pattern.compile("(?i)(password|pwd|secret|key)"));
    }

    /**
     * 定义数据脱敏规则
     */
    public void defineDataMaskRule(DataMaskRule rule) {
        log.info("定义数据脱敏规则: ruleId={}, dataType={}", rule.getRuleId(), rule.getDataType());
        maskRules.put(rule.getRuleId(), rule);

        try {
            String cacheKey = MASK_RULE_PREFIX + rule.getRuleId();
            String ruleJson = objectMapper.writeValueAsString(rule);
            stringRedisTemplate.opsForValue().set(cacheKey, ruleJson, Duration.ofDays(30));
        } catch (JsonProcessingException e) {
            log.error("缓存脱敏规则失败: ruleId={}", rule.getRuleId(), e);
        }
    }

    /**
     * 脱敏数据
     */
    public String maskData(String data, String dataType) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        // 查找适用的脱敏规则
        DataMaskRule rule = maskRules.values().stream()
                .filter(r -> r.isEnabled() && r.getDataType().equals(dataType))
                .findFirst()
                .orElse(null);

        if (rule != null) {
            return applyMaskRule(data, rule);
        }

        // 使用默认脱敏策略
        return applyDefaultMask(data, dataType);
    }

    /**
     * 应用脱敏规则
     */
    private String applyMaskRule(String data, DataMaskRule rule) {
        if (data.length() <= rule.getKeepStart() + rule.getKeepEnd()) {
            return data;
        }

        String replacement = rule.getReplacement() != null ? rule.getReplacement() : "*";
        int maskLength = data.length() - rule.getKeepStart() - rule.getKeepEnd();
        String mask = replacement.repeat(Math.max(1, maskLength));

        return data.substring(0, rule.getKeepStart()) + mask +
               data.substring(data.length() - rule.getKeepEnd());
    }

    /**
     * 应用默认脱敏策略
     */
    private String applyDefaultMask(String data, String dataType) {
        switch (dataType) {
            case "PHONE":
                // 手机号：保留前3后4
                if (data.length() >= 11) {
                    return data.substring(0, 3) + "****" + data.substring(data.length() - 4);
                }
                break;
            case "ID_CARD":
                // 身份证：保留前6后4
                if (data.length() >= 18) {
                    return data.substring(0, 6) + "********" + data.substring(data.length() - 4);
                }
                break;
            case "EMAIL":
                // 邮箱：保留@前2字符和域名
                int atIndex = data.indexOf('@');
                if (atIndex > 2) {
                    return data.substring(0, 2) + "***" + data.substring(atIndex);
                }
                break;
            case "BANK_CARD":
                // 银行卡：保留前4后4
                if (data.length() >= 16) {
                    return data.substring(0, 4) + " **** **** " + data.substring(data.length() - 4);
                }
                break;
            case "NAME":
                // 姓名：保留姓
                if (data.length() >= 2) {
                    return data.charAt(0) + "*".repeat(data.length() - 1);
                }
                break;
            case "ADDRESS":
                // 地址：保留前6字符
                if (data.length() > 6) {
                    return data.substring(0, 6) + "****";
                }
                break;
        }

        // 默认：保留前后各1/4
        int keepLength = Math.max(1, data.length() / 4);
        if (data.length() > keepLength * 2) {
            return data.substring(0, keepLength) + "***" + data.substring(data.length() - keepLength);
        }

        return data;
    }

    /**
     * 批量脱敏数据
     */
    public Map<String, Object> maskRowData(Map<String, Object> rowData, Set<String> columnsToMask,
                                           Map<String, String> columnDataTypes) {
        Map<String, Object> maskedData = new HashMap<>(rowData);

        for (String column : columnsToMask) {
            if (maskedData.containsKey(column)) {
                Object value = maskedData.get(column);
                if (value instanceof String) {
                    String dataType = columnDataTypes.getOrDefault(column, "DEFAULT");
                    maskedData.put(column, maskData((String) value, dataType));
                }
            }
        }

        return maskedData;
    }

    /**
     * 自动检测并脱敏敏感数据
     */
    public String autoMaskSensitiveData(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        String result = data;

        for (Map.Entry<String, Pattern> entry : SENSITIVE_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(result).find()) {
                result = entry.getValue().matcher(result)
                        .replaceAll(match -> maskData(match.group(), entry.getKey()));
            }
        }

        return result;
    }

    /**
     * 匿名化数据
     */
    public String anonymizeData(String data, String dataType) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        // 生成匿名化标识
        String anonymousId = generateAnonymousId(data);

        // 记录映射关系（可选，用于需要还原的场景）
        recordAnonymousMapping(data, anonymousId, dataType);

        return anonymousId;
    }

    /**
     * 生成匿名化ID
     */
    private String generateAnonymousId(String data) {
        return "ANON_" + securityManagerComponent.hashPassword(data).substring(0, 16);
    }

    /**
     * 记录匿名化映射
     */
    private void recordAnonymousMapping(String original, String anonymous, String dataType) {
        try {
            String cacheKey = "security:anonymous:" + anonymous;
            Map<String, String> mapping = new HashMap<>();
            mapping.put("original", securityManagerComponent.encryptData(original));
            mapping.put("dataType", dataType);
            mapping.put("createdTime", LocalDateTime.now().toString());

            stringRedisTemplate.opsForHash().putAll(cacheKey, mapping);
            stringRedisTemplate.expire(cacheKey, Duration.ofDays(365));
        } catch (Exception e) {
            log.error("记录匿名化映射失败", e);
        }
    }
}
